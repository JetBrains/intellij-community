// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.inspector.themePicker

import com.intellij.diagnostic.VMOptions
import com.intellij.ide.ui.RegistryBooleanOptionDescriptor
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.MouseShortcut
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.components.*
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.runAndLogException
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.wm.IdeGlassPane
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.CollectionListModel
import com.intellij.ui.JBColor
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.components.JBList
import com.intellij.ui.content.impl.ContentImpl
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.listCellRenderer.listCellRenderer
import com.intellij.util.SystemProperties
import com.intellij.util.ui.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.awt.*
import java.awt.event.InputEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseMotionAdapter
import java.lang.ref.WeakReference
import java.util.*
import java.util.function.BiFunction
import javax.swing.*

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

@Service(Service.Level.APP)
internal class UiThemeColorPicker(internal val coroutineScope: CoroutineScope) {

  private var disposable: Disposable? = null

  private val colorMap = WeakHashMap<Window, PixelColorMap>()
  private var currentPopup: WeakReference<JBPopup>? = null
  internal var hoverState = MutableStateFlow<List<ThemeColorInfo>>(emptyList())

  companion object {
    @JvmStatic
    fun getInstance(): UiThemeColorPicker = service()
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

    currentPopup?.get()?.cancel()
    currentPopup = null

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
          currentPopup?.get()?.cancel()
          currentPopup = null
          return
        }

        val colors = getColorsAt(point)
        showHoverPopup(colors, point)
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

  private fun showHoverPopup(colors: List<ThemeColorInfo>, point: RelativePoint) {
    val oldPopup = currentPopup?.get()
    if (oldPopup != null && !oldPopup.isDisposed) {
      val oldColors = oldPopup.content.getClientProperty(THEME_VIEWER_UI_MARKER_KEY)
      if (oldColors == colors) return
      oldPopup.cancel()
    }
    if (colors.isEmpty()) return
    if (!service<UiThemeColorPickerState>().state.showPopup) return

    val popup = JBPopupFactory.getInstance().createComponentPopupBuilder(
      panel {
        row {
          cell(createThemeColorList())
            .applyToComponent { model = CollectionListModel(colors) }
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

    currentPopup = WeakReference(popup)
    popup.show(RelativePoint(point.component, Point(point.point.x + 100, point.point.y + 30)))
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
  override fun apply(component: JComponent, g: Graphics2D): Graphics2D? {
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
        val editorRegistry = SystemProperties.getBooleanProperty("editor.color.scheme.mark.colors", false)
        val mixerRegistry = Registry.get("ide.color.mixture.mark.colors")
        checkBox("Enable color markers in runtime").applyToComponent {
          isSelected = editorRegistry && mixerRegistry.asBoolean()
          toolTipText = "May affect IDE performance and behavior on theme changes"
          addItemListener {
            invokeLater { // swing weirdness
              if (isSelected) {
                mixerRegistry.setValue(true)

                if (canWriteVMOptions) {
                  logger<UiThemeColorPickerToolWindowFactory>().runAndLogException {
                    VMOptions.setProperty("editor.color.scheme.mark.colors", "true")
                  }
                }
              }
              else {
                mixerRegistry.resetToDefault()

                if (canWriteVMOptions) {
                  logger<UiThemeColorPickerToolWindowFactory>().runAndLogException {
                    VMOptions.setProperty("editor.color.scheme.mark.colors", null)
                  }
                }
              }
              RegistryBooleanOptionDescriptor.suggestRestartIfNecessary(null)
            }
          }
        }
        comment(if (canWriteVMOptions) "Restart required" else "Also set '-Deditor.color.scheme.mark.colors=true' VMOption. Restart required")
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
        scrollCell(createThemeColorList())
          .applyToComponent {
            manager.coroutineScope.launch {
              manager.hoverState.collectLatest { colors ->
                model = CollectionListModel(colors)
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

private fun createThemeColorList(): JBList<ThemeColorInfo> {
  val list = JBList<ThemeColorInfo>()
  list.cellRenderer = listCellRenderer {
    val value = this.value
    when (value) {
      is ThemeColorInfo.ColorInfo -> {
        icon(value.color.toIcon())
        text(getColorId(value.color))
      }
      is ThemeColorInfo.AttributeInfo -> {
        text("Attrubute: ")
        value.value.backgroundColor?.let { text(getColorId(it)) }
        value.value.foregroundColor?.let { text(getColorId(it)) }
        value.value.errorStripeColor?.let { text(getColorId(it)) }
        value.value.effectColor?.let { text(getColorId(it)) }
      }
      is ThemeColorInfo.AttributeKeyInfo -> {
        text("AttributeKey: " + value.key.externalName)
      }
    }
  }
  return list
}

private fun getColorId(color: Color): String {
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

  return color.toString()
}

internal sealed interface ThemeColorInfo {
  class ColorInfo(val color: Color) : ThemeColorInfo
  class AttributeKeyInfo(val key: TextAttributesKey) : ThemeColorInfo
  class AttributeInfo(val value: TextAttributes) : ThemeColorInfo
}

private fun getRootPane(window: Any): JRootPane? {
  if (window is RootPaneContainer) {
    return window.rootPane
  }
  return null
}

private fun Color.toIcon(): Icon = ColorIcon(16, this)