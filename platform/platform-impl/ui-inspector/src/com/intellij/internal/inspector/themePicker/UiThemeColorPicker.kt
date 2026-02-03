// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.inspector.themePicker

import com.intellij.diagnostic.VMOptions
import com.intellij.ide.ui.RegistryBooleanOptionDescriptor
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.MouseShortcut
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.rethrowControlFlowException
import com.intellij.openapi.diagnostic.runAndLogException
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.colors.impl.AbstractColorsScheme.ENABLE_RUNTIME_SCHEME_COLOR_WRAPPER_OPTION
import com.intellij.openapi.editor.ex.util.LayeredTextAttributes
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.wm.IdeGlassPane
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.ColorMixture.Companion.ENABLE_RUNTIME_COLOR_MIXTURE_WRAPPER_OPTION
import com.intellij.ui.ColoredTreeCellRenderer
import com.intellij.ui.JBColor
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.content.impl.ContentImpl
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.SystemProperties
import com.intellij.util.ui.ColorIcon
import com.intellij.util.ui.JBSwingUtilities
import com.intellij.util.ui.PresentableColor
import com.intellij.util.ui.StartupUiUtil
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.tree.TreeUtil
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.AWTEvent
import java.awt.Color
import java.awt.Component
import java.awt.Graphics2D
import java.awt.Point
import java.awt.Rectangle
import java.awt.Window
import java.awt.event.InputEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseMotionAdapter
import java.lang.ref.WeakReference
import java.util.WeakHashMap
import java.util.function.BiFunction
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JRootPane
import javax.swing.JTree
import javax.swing.RootPaneContainer
import javax.swing.SwingUtilities
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreeModel
import kotlin.time.Duration.Companion.milliseconds

private const val THEME_VIEWER_UI_MARKER_KEY = "ThemeColorPopupIdentity"
private const val TOOL_WINDOW_ID = "UI Theme Color Picker"

private val UPDATE_TABLE_SHORTCUT = MouseShortcut(MouseEvent.BUTTON1, InputEvent.CTRL_DOWN_MASK or InputEvent.ALT_DOWN_MASK or InputEvent.SHIFT_DOWN_MASK, 1)

@Service(Service.Level.APP)
@State(name = "UiThemeColorPickerState", storages = [Storage("other.xml")])
internal class UiThemeColorPickerState : SimplePersistentStateComponent<UiThemeColorPickerState.State>(State()) {
  class State : BaseState() {
    var showPopup = true
  }
}

private data class PopupState(val colors: List<ThemeColorInfo>, val point: RelativePoint)

@OptIn(FlowPreview::class)
@Service(Service.Level.APP)
internal class UiThemeColorPicker(internal val coroutineScope: CoroutineScope) {

  private var disposable: Disposable? = null

  private val colorMap = WeakHashMap<Window, PixelColorMap>()
  private val popupState = MutableStateFlow<PopupState?>(null)
  internal var hoverState = MutableStateFlow<List<ThemeColorInfo>>(emptyList())

  companion object {
    @JvmStatic
    fun getInstance(): UiThemeColorPicker = service()
  }
  
  init {
    coroutineScope.launch(CoroutineName("UiThemeColorPicker.popup")) {
      // weak reference just in case the popup is hidden from outside and our service is stuck for some reason
      var currentPopup: WeakReference<JBPopup>? = null
      popupState.debounce(50.milliseconds).collectLatest { popupState ->
        try {
          // We should really use UI instead of EDT here, but because TreeUtil requires the WIL (sic!),
          // for now we can't get rid of the EDT here.
          if (popupState == null) {
            withContext(Dispatchers.EDT) {
              currentPopup?.get()?.cancel()
            }
            currentPopup = null
          }
          else {
            val newPopup = withContext(Dispatchers.EDT) {
              showHoverPopup(currentPopup?.get(), popupState.colors, popupState.point)
            }
            currentPopup = newPopup?.let { WeakReference(it) }
          }
        }
        catch (e: Exception) {
          rethrowControlFlowException(e)
          LOG.warn("Could not show a popup corresponding to the state $popupState", e)
          withContext(Dispatchers.EDT) {
            currentPopup?.get()?.cancel() // it's outdated anyway
          }
          currentPopup = null
        }
      }
    }
  }

  fun isEnabled(): Boolean {
    return disposable != null
  }

  fun setEnabled(value: Boolean) {
    if (!value) {
      disposable?.let { Disposer.dispose(it) }
      disposable = null
      return
    }
    if (disposable != null) return

    val disposable = Disposer.newDisposable(ApplicationManager.getApplication())
    this.disposable = disposable

    Disposer.register(disposable) { cleanup() }

    val graphicsDisposable = JBSwingUtilities.addGlobalCGTransform(ThemeColorPickerTransform())
    Disposer.register(disposable, graphicsDisposable)

    for (window in Window.getWindows()) {
      setupWindowListeners(window, disposable)
    }

    // need COMPONENT_EVENT_MASK to handle heavy window popups
    val mask = AWTEvent.WINDOW_EVENT_MASK or AWTEvent.WINDOW_STATE_EVENT_MASK or AWTEvent.COMPONENT_EVENT_MASK
    StartupUiUtil.addAwtListener(mask, disposable) { event ->
      val source = event.source
      if (source is Window) {
        setupWindowListeners(source, disposable)
      }
    }

    for (project in ProjectManager.getInstance().openProjects) {
      val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(TOOL_WINDOW_ID)
      toolWindow?.setAvailable(true)
      toolWindow?.activate(null)
    }
  }

  private fun cleanup() {
    colorMap.clear()

    popupState.value = null

    hoverState.tryEmit(emptyList())

    for (project in ProjectManager.getInstance().openProjects) {
      val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(TOOL_WINDOW_ID)
      toolWindow?.setAvailable(false)
    }
  }

  private fun setupWindowListeners(window: Window, disposable: Disposable) {
    val rootPane = getRootPane(window) ?: return
    val glassPane = rootPane.glassPane as? IdeGlassPane ?: return

    val mouseMoveListener = object : MouseMotionAdapter() {
      override fun mouseMoved(e: MouseEvent) {
        val point = RelativePoint(e)
        if (isOurOwnUi(point)) {
          popupState.value = null
          return
        }

        val colors = getColorsAt(point)
        popupState.value = PopupState(colors, point)
      }
    }
    val mouseClickListener = object : MouseAdapter() {
      override fun mousePressed(e: MouseEvent) {
        if (UPDATE_TABLE_SHORTCUT == KeymapUtil.createMouseShortcut(e)) {
          val point = RelativePoint(e)
          if (isOurOwnUi(point)) return

          val colors = getColorsAt(point)
          hoverState.tryEmit(colors)
          e.consume()
        }
      }
    }

    glassPane.addMousePreprocessor(mouseClickListener, disposable)
    glassPane.addMouseMotionPreprocessor(mouseMoveListener, disposable)
    window.repaint()
  }

  private fun showHoverPopup(oldPopup: JBPopup?, colors: List<ThemeColorInfo>, point: RelativePoint): JBPopup? {
    if (oldPopup != null && !oldPopup.isDisposed) {
      val oldColors = oldPopup.content.getClientProperty(THEME_VIEWER_UI_MARKER_KEY)
      if (oldColors == colors) return oldPopup
      oldPopup.cancel()
    }
    if (colors.isEmpty()) return null
    if (!service<UiThemeColorPickerState>().state.showPopup) return null

    val popup = JBPopupFactory.getInstance().createComponentPopupBuilder(
      panel {
        row {
          cell(createThemeColorTree())
            .applyToComponent {
              val tree = this
              tree.model = createColorsTreeModel(colors)
              TreeUtil.expandAll(tree)
            }
        }
      },
      null
    )
      .setRequestFocus(false)
      .setResizable(false)
      .setMovable(false)
      .setCancelOnClickOutside(false)
      .setCancelOnWindowDeactivation(true)
      .createPopup()
    popup.content.putClientProperty(THEME_VIEWER_UI_MARKER_KEY, colors)

    popup.show(RelativePoint(point.component, Point(point.point.x + 100, point.point.y + 30)))
    return popup
  }

  fun storeColorForPixel(component: JComponent, rectangle: Rectangle, color: Color, erase: Boolean) {
    if (!isEnabled()) return

    val window = UIUtil.getWindow(component) ?: return
    val colorMap = colorMap.computeIfAbsent(window) { PixelColorMap() }

    val rootPane = getRootPane(window)!!
    val windowRectangle = SwingUtilities.convertRectangle(component, rectangle, rootPane)
    colorMap.mark(windowRectangle, color, erase)
  }

  private fun getColorsAt(point: RelativePoint): List<ThemeColorInfo> {
    if (!isEnabled()) return emptyList()

    return getDrawColorsAt(point).orEmpty() +
           EditorColorPicker.getEditorColorsAt(point).orEmpty()
  }

  private fun getDrawColorsAt(point: RelativePoint): List<ThemeColorInfo>? {
    val window = UIUtil.getWindow(point.component) ?: return null
    val componentMap = colorMap[window] ?: return null

    val rootPane = getRootPane(window)!!
    val windowPoint = point.getPoint(rootPane)

    return componentMap.getColor(windowPoint)
  }
}

private class ThemeColorPickerTransform : BiFunction<JComponent, Graphics2D, Graphics2D?> {
  override fun apply(component: JComponent, g: Graphics2D): Graphics2D {
    if (isOurOwnUi(component)) {
      return g
    }

    return ColorPickerGraphicsDelegate.wrap(g, component)
  }
}

private fun isOurOwnUi(point: RelativePoint): Boolean {
  val component = UIUtil.getDeepestComponentAt(point.component, point.point.x, point.point.y) ?: return false
  return isOurOwnUi(component)
}

private fun isOurOwnUi(component: Component): Boolean {
  return UIUtil.uiParents(component, false).any { it is JComponent && it.getClientProperty(THEME_VIEWER_UI_MARKER_KEY) != null }
}

internal class UiThemeColorPickerToolWindowFactory : ToolWindowFactory, DumbAware {
  override suspend fun isApplicableAsync(project: Project): Boolean {
    return ApplicationManager.getApplication().isInternal
  }

  override fun shouldBeAvailable(project: Project): Boolean {
    return false
  }

  override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
    val manager = UiThemeColorPicker.getInstance()
    val panel = panel {
      row {
        val canWriteVMOptions = VMOptions.canWriteOptions()
        val editorRegistry = SystemProperties.getBooleanProperty(ENABLE_RUNTIME_SCHEME_COLOR_WRAPPER_OPTION, false)
        val mixerRegistry = SystemProperties.getBooleanProperty(ENABLE_RUNTIME_COLOR_MIXTURE_WRAPPER_OPTION, false)
        checkBox("Enable color markers in runtime").applyToComponent {
          isSelected = editorRegistry && mixerRegistry
          isEnabled = canWriteVMOptions
          toolTipText = "May affect IDE performance and behavior on theme changes"
          addItemListener {
            invokeLater { // swing weirdness
              if (isSelected) {
                if (canWriteVMOptions) {
                  logger<UiThemeColorPickerToolWindowFactory>().runAndLogException {
                    VMOptions.setProperty(ENABLE_RUNTIME_SCHEME_COLOR_WRAPPER_OPTION, "true")
                    VMOptions.setProperty(ENABLE_RUNTIME_COLOR_MIXTURE_WRAPPER_OPTION, "true")
                  }
                }
              }
              else {
                if (canWriteVMOptions) {
                  logger<UiThemeColorPickerToolWindowFactory>().runAndLogException {
                    VMOptions.setProperty(ENABLE_RUNTIME_SCHEME_COLOR_WRAPPER_OPTION, null)
                    VMOptions.setProperty(ENABLE_RUNTIME_COLOR_MIXTURE_WRAPPER_OPTION, null)
                  }
                }
              }
              RegistryBooleanOptionDescriptor.suggestRestartIfNecessary(null)
            }
          }
        }.comment(if (canWriteVMOptions) null
                  else "Set '-D${ENABLE_RUNTIME_SCHEME_COLOR_WRAPPER_OPTION}=true' and \n" +
                       "'-D${ENABLE_RUNTIME_COLOR_MIXTURE_WRAPPER_OPTION}=true' VMOptions manually.")
        if (canWriteVMOptions) {
          comment("Restart required")
        }
      }
      row {
        checkBox("Show on-hover tooltip").applyToComponent {
          isSelected = service<UiThemeColorPickerState>().state.showPopup
          addItemListener { service<UiThemeColorPickerState>().state.showPopup = isSelected }
        }
      }
      row {
        comment("Use ${KeymapUtil.getShortcutText(UPDATE_TABLE_SHORTCUT)} to show hovered colors below")
      }
      row {
        scrollCell(createThemeColorTree())
          .applyToComponent {
            val tree = this
            manager.coroutineScope.launch(Dispatchers.EDT) {
              manager.hoverState.collectLatest { colors ->
                tree.model = createColorsTreeModel(colors)
                TreeUtil.expandAll(tree)
              }
            }
          }.align(Align.FILL)
      }.resizableRow()
    }
    panel.putClientProperty(THEME_VIEWER_UI_MARKER_KEY, true)

    val content = ContentImpl(panel, null, false)
    content.isCloseable = false
    toolWindow.contentManager.addContent(content)
  }

}

private fun createColorsTreeModel(allInfos: List<ThemeColorInfo>): TreeModel {
  val root = DefaultMutableTreeNode("root")

  fun String.shortenLabel(): String {
    return StringUtil.shortenTextWithEllipsis(this.replace("\n", " "), 20, 5)
  }

  fun DefaultMutableTreeNode.labelNode(label: String): DefaultMutableTreeNode {
    val child = DefaultMutableTreeNode(ThemeTreeNode.TextNode(label))
    this.add(child)
    return child
  }

  fun DefaultMutableTreeNode.colorNode(color: Color, label: String): DefaultMutableTreeNode {
    val child = DefaultMutableTreeNode(ThemeTreeNode.ColorNode(color, label))
    this.add(child)
    return child
  }

  fun DefaultMutableTreeNode.attributesNodes(textAttributes: TextAttributes?, recursiveCall: Boolean = false) {
    if (textAttributes == null) return

    if (textAttributes === TextAttributes.ERASE_MARKER) {
      labelNode("ERASE MARKER")
      return
    }

    if (textAttributes is LayeredTextAttributes) {
      val keysGroup = labelNode("LayeredTextAttributes")
      for (key in textAttributes.keys) {
        keysGroup.labelNode(key.externalName)
      }

      val upToDateValue = LayeredTextAttributes.create(EditorColorsManager.getInstance().globalScheme, textAttributes.keys)
      if (upToDateValue != textAttributes && !recursiveCall) {
        val upToDate = keysGroup.labelNode("OUTDATED! Up to date value:")
        upToDate.attributesNodes(upToDateValue, recursiveCall = true)
      }
    }

    textAttributes.backgroundColor?.let {
      colorNode(it, "Background")
    }
    textAttributes.foregroundColor?.let {
      colorNode(it, "Foreground")
    }
    textAttributes.errorStripeColor?.let {
      colorNode(it, "Error Stripe")
    }
    textAttributes.effectColor?.let {
      colorNode(it, "Effect")
    }
  }

  val colorInfos = allInfos.filterIsInstance<ThemeColorInfo.ColorInfo>()
  val highlighterInfos = allInfos.filterIsInstance<ThemeColorInfo.HighlighterInfo>()
  val syntaxInfos = allInfos.filterIsInstance<ThemeColorInfo.SyntaxInfo>()

  if (colorInfos.isNotEmpty()) {
    val colorsRoot = if (colorInfos.size != allInfos.size) root.labelNode("Colors") else root
    for (info in colorInfos) {
      val label = getColorPresentation(info.color)
      colorsRoot.colorNode(info.color, label)
    }
  }

  for (highlighterInfo in highlighterInfos) {
    val infoRoot = root.labelNode("Highlighter '${highlighterInfo.text.shortenLabel()}'")

    if (highlighterInfo.forcedAttributes != null) {
      val forcedNode = infoRoot.labelNode("Forced attributes")
      forcedNode.attributesNodes(highlighterInfo.forcedAttributes)
    }

    if (highlighterInfo.key != null) {
      val label = buildString {
        append("Attribute Key")
        if (highlighterInfo.forcedAttributes != null) append(" (overwritten)")
      }
      val keysNode = infoRoot.labelNode(label)
      keysNode.labelNode(highlighterInfo.key.externalName)
    }

    if (highlighterInfo.forcedAttributes == null && highlighterInfo.key == null && highlighterInfo.attributes != null) {
      infoRoot.attributesNodes(highlighterInfo.attributes)
    }
  }

  for (syntaxInfo in syntaxInfos) {
    val infoRoot = root.labelNode("Syntax '${syntaxInfo.text.shortenLabel()}'")

    if (syntaxInfo.attributeKeys.isNotEmpty()) {
      val keysGroup = infoRoot.labelNode("Keys")
      for (key in syntaxInfo.attributeKeys) {
        keysGroup.labelNode(key.externalName)
      }
    }

    infoRoot.attributesNodes(syntaxInfo.attributes)
  }

  return DefaultTreeModel(root)
}

private fun createThemeColorTree(): JTree {
  val tree = Tree()
  tree.isRootVisible = false
  tree.showsRootHandles = true
  tree.cellRenderer = object : ColoredTreeCellRenderer() {
    override fun customizeCellRenderer(tree: JTree, node: Any?, selected: Boolean, expanded: Boolean, leaf: Boolean, row: Int, hasFocus: Boolean) {
      val value = (node as? DefaultMutableTreeNode)?.userObject as? ThemeTreeNode
      when (value) {
        is ThemeTreeNode.ColorNode -> {
          append(getColorPresentation(value.color))
          icon = value.color.toIcon()
        }
        is ThemeTreeNode.TextNode -> {
          append(value.label)
          icon = null
        }
        null -> {
          logger<UiThemeColorPickerToolWindowFactory>().error("Unexpected tree node: $node")
        }
      }
    }
  }
  return tree
}

private sealed interface ThemeTreeNode {
  class TextNode(val label: String) : ThemeTreeNode {
    override fun toString(): String = label
  }

  class ColorNode(val color: Color, val label: String) : ThemeTreeNode {
    override fun toString(): String = label
  }
}

private fun getColorPresentation(color: Color): String {
  val id = getColorId(color)
  val hex = UIUtil.colorToHex(color)
  if (id != null) {
    return "$id #$hex"
  }
  else {
    return "#$hex"
  }
}

private fun getColorId(color: Color): String? {
  if (color is PresentableColor) {
    val name = color.getPresentableName()
    if (name != null) {
      return name
    }

    // Check if it's one of the static constants
    when {
      color === JBColor.RED -> return "JBColor.RED"
      color === JBColor.BLUE -> return "JBColor.BLUE"
      color === JBColor.WHITE -> return "JBColor.WHITE"
      color === JBColor.BLACK -> return "JBColor.BLACK"
      color === JBColor.GRAY -> return "JBColor.GRAY"
      color === JBColor.LIGHT_GRAY -> return "JBColor.LIGHT_GRAY"
      color === JBColor.DARK_GRAY -> return "JBColor.DARK_GRAY"
      color === JBColor.PINK -> return "JBColor.PINK"
      color === JBColor.ORANGE -> return "JBColor.ORANGE"
      color === JBColor.YELLOW -> return "JBColor.YELLOW"
      color === JBColor.GREEN -> return "JBColor.GREEN"
      color === JBColor.MAGENTA -> return "JBColor.MAGENTA"
      color === JBColor.CYAN -> return "JBColor.CYAN"
    }
  }

  return null
}

internal sealed interface ThemeColorInfo {
  class ColorInfo(val color: Color) : ThemeColorInfo

  class HighlighterInfo(
    val text: String,
    val forcedAttributes: TextAttributes?,
    val key: TextAttributesKey?,
    val attributes: TextAttributes?,
  ) : ThemeColorInfo {
    val isEmpty: Boolean get() = forcedAttributes == null && key == null && attributes == null
  }

  class SyntaxInfo(
    val text: String,
    val attributeKeys: Array<TextAttributesKey>,
    val attributes: TextAttributes?,
  ) : ThemeColorInfo {
    val isEmpty: Boolean get() = attributeKeys.isEmpty() && attributes == null
  }

}

private fun getRootPane(window: Any): JRootPane? {
  if (window is RootPaneContainer) {
    return window.rootPane
  }
  return null
}

private fun Color.toIcon(): Icon = ColorIcon(16, this)

private val LOG = logger<UiThemeColorPicker>()
