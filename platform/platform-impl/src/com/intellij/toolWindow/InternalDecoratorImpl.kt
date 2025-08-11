// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.toolWindow

import com.intellij.accessibility.AccessibilityUtils
import com.intellij.ide.IdeBundle
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.application.impl.InternalUICustomization
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.ui.Divider
import com.intellij.openapi.ui.Queryable
import com.intellij.openapi.ui.Splitter
import com.intellij.openapi.util.CheckedDisposable
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.wm.*
import com.intellij.openapi.wm.impl.InternalDecorator
import com.intellij.openapi.wm.impl.ToolWindowExternalDecorator
import com.intellij.openapi.wm.impl.ToolWindowImpl
import com.intellij.openapi.wm.impl.content.ToolWindowContentUi
import com.intellij.openapi.wm.impl.isInternal
import com.intellij.ui.*
import com.intellij.ui.components.panels.Wrapper
import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentManager
import com.intellij.ui.content.ContentManagerEvent
import com.intellij.ui.content.ContentManagerListener
import com.intellij.ui.content.impl.ContentImpl
import com.intellij.ui.content.impl.ContentManagerImpl
import com.intellij.ui.hover.HoverStateListener
import com.intellij.ui.paint.LinePainter2D
import com.intellij.util.MathUtil
import com.intellij.util.SmartList
import com.intellij.util.animation.AlphaAnimated
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import org.intellij.lang.annotations.MagicConstant
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.ApiStatus.Internal
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.util.*
import javax.accessibility.AccessibleContext
import javax.accessibility.AccessibleRole
import javax.swing.*
import javax.swing.border.Border
import javax.swing.plaf.UIResource
import javax.swing.text.JTextComponent

@ApiStatus.Internal
class InternalDecoratorImpl internal constructor(
  @JvmField internal val toolWindow: ToolWindowImpl,
  private val contentUi: ToolWindowContentUi,
  private val myDecoratorChild: JComponent
) : InternalDecorator(), Queryable, UiDataProvider, ComponentWithMnemonics {
  companion object {
    val SHARED_ACCESS_KEY: Key<Boolean> = Key.create("sharedAccess")

    internal val HIDE_COMMON_TOOLWINDOW_BUTTONS: Key<Boolean> = Key.create("HideCommonToolWindowButtons")
    internal val INACTIVE_LOOK: Key<Boolean> = Key.create("InactiveLook")

    private val PREVENT_RECURSIVE_BACKGROUND_CHANGE = Key.create<Boolean>("prevent.recursive.background.change")

    /**
     * Catches all event from tool window and modifies decorator's appearance.
     */
    internal const val HIDE_ACTIVE_WINDOW_ACTION_ID: String = "HideActiveWindow"

    private fun moveContent(content: Content, source: InternalDecoratorImpl, target: InternalDecoratorImpl) {
      val targetContentManager = target.contentManager
      if (content.manager == targetContentManager) {
        return
      }

      val initialState = content.getUserData(Content.TEMPORARY_REMOVED_KEY)
      try {
        source.setSplitUnsplitInProgress(true)
        content.putUserData(Content.TEMPORARY_REMOVED_KEY, java.lang.Boolean.TRUE)
        content.manager?.removeContent(content, false)
        (content as ContentImpl).manager = targetContentManager
        targetContentManager.addContent(content)
      }
      finally {
        content.putUserData(Content.TEMPORARY_REMOVED_KEY, initialState)
        source.setSplitUnsplitInProgress(false)
      }
    }

    /**
     * Installs a focus traversal policy for the tool window.
     * If the policy cannot handle a keystroke, it delegates the handling to
     * the nearest ancestors focus traversal policy. For instance,
     * this policy does not handle KeyEvent.VK_ESCAPE, so it can delegate the handling
     * to a ThreeComponentSplitter instance.
     */
    fun installFocusTraversalPolicy(container: Container, policy: FocusTraversalPolicy) {
      container.isFocusCycleRoot = true
      container.isFocusTraversalPolicyProvider = true
      container.focusTraversalPolicy = policy
      installDefaultFocusTraversalKeys(container, KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS)
      installDefaultFocusTraversalKeys(container, KeyboardFocusManager.BACKWARD_TRAVERSAL_KEYS)
    }

    @JvmStatic
    fun findTopLevelDecorator(component: Component?): InternalDecoratorImpl? {
      var parent: Component? = component?.parent
      var candidate: InternalDecoratorImpl? = null
      while (parent != null) {
        if (parent is InternalDecoratorImpl) {
          candidate = parent
        }
        parent = parent.parent
      }
      return candidate
    }

    @JvmStatic
    fun findNearestDecorator(component: Component?): InternalDecoratorImpl? {
      return ComponentUtil.findParentByCondition(component?.parent) { it is InternalDecoratorImpl } as InternalDecoratorImpl?
    }

    @JvmStatic
    @Internal
    fun componentWithEditorBackgroundAdded(component: Component) {
      val decorator = findNearestDecorator(component)
      if (decorator != null) {
        decorator.componentsWithEditorLikeBackground += component
      }
    }

    @JvmStatic
    @Internal
    fun componentWithEditorBackgroundRemoved(component: Component) {
      val decorator = findNearestDecorator(component)
      if (decorator != null) {
        decorator.componentsWithEditorLikeBackground -= component
      }
    }

    @JvmStatic
    internal fun setActiveDecorator(toolWindow: ToolWindow, focusOwner: Component) {
      val decorator = findNearestDecorator(focusOwner)
      if (decorator?.toolWindowId == toolWindow.id) {
        findTopLevelDecorator(decorator.header)?.forAllNestedDecorators {
          val newValue = if (it == decorator) null else java.lang.Boolean.TRUE
          val oldValue = it.getClientProperty(INACTIVE_LOOK)
          if (newValue != oldValue) {
            it.putClientProperty(INACTIVE_LOOK, newValue)
            it.header.repaint()
          }
        }
      }
    }

    /** Checks if the tool window header should have a top border. */
    @JvmStatic
    internal fun headerNeedsTopBorder(header: ToolWindowHeader): Boolean {
      val decorator = findNearestDecorator(header) ?: return false
      return decorator.toolWindow.type == ToolWindowType.WINDOWED &&
          SideProperty.TOP_TOOL_WINDOW_EDGE in decorator.getSideProperties(decorator)
    }

    @JvmStatic
    fun preventRecursiveBackgroundUpdateOnToolwindow(component: JComponent) {
      component.putClientProperty(PREVENT_RECURSIVE_BACKGROUND_CHANGE, true)
    }

    private fun isRecursiveBackgroundUpdateDisabled(component: Component): Boolean {
      val preventRecoloring = (component as? JComponent)?.getClientProperty(PREVENT_RECURSIVE_BACKGROUND_CHANGE) ?: return false

      return preventRecoloring == true
    }

    internal fun setBackgroundFor(component: Component, bg: Color) {
      if (component is ActionButton ||
          component is Divider ||
          component is JTextComponent ||
          component is JComboBox<*> ||
          component is EditorTextField) return
      if (component.isBackgroundSet && component.background !is UIResource) {
        return
      }
      component.background = bg
    }

    internal fun setBackgroundRecursively(component: Component, bg: Color) {
      UIUtil.uiTraverser(component)
        .expandAndFilter { !isRecursiveBackgroundUpdateDisabled(component) }
        .forEach { setBackgroundFor(it, bg) }
    }

    private fun installDefaultFocusTraversalKeys(container: Container, id: Int) {
      container.setFocusTraversalKeys(id, KeyboardFocusManager.getCurrentKeyboardFocusManager().getDefaultFocusTraversalKeys(id))
    }

    private val HOVER_STATE_LISTENER: HoverStateListener = object : HoverStateListener() {
      override fun hoverChanged(component: Component, hovered: Boolean) {
        if (component is InternalDecoratorImpl) {
          component.isWindowHovered = hovered
          component.updateActiveAndHoverState()
        }
      }
    }
  }

  enum class Mode {
    SINGLE, VERTICAL_SPLIT, HORIZONTAL_SPLIT, CELL;

    val isSplit: Boolean
      get() = this == VERTICAL_SPLIT || this == HORIZONTAL_SPLIT
  }

  var mode: Mode? = null
  private var isSplitUnsplitInProgress = false
  private var isWindowHovered = false
  private var divider: JPanel? = null
  private val dividerAndHeader = JPanel(BorderLayout())
  private var disposable: CheckedDisposable? = null
  val header: ToolWindowHeader
  private val notificationHeader = Wrapper()
  private var firstDecorator: InternalDecoratorImpl? = null
  private var secondDecorator: InternalDecoratorImpl? = null
  private var splitter: Splitter? = null
  private val componentsWithEditorLikeBackground = SmartList<Component>()
  private var tabActions: List<AnAction> = emptyList()
  private val titleActions = mutableListOf<AnAction>()

  init {
    isFocusable = false
    focusTraversalPolicy = LayoutFocusTraversalPolicy()
    updateMode(Mode.SINGLE)
    header = object : ToolWindowHeader(toolWindow, contentUi, gearProducer = { toolWindow.createPopupGroup(true) }) {
      override val isActive: Boolean
        get() {
          return toolWindow.isActive && !toolWindow.toolWindowManager.isNewUi &&
                 ClientProperty.get(this@InternalDecoratorImpl, INACTIVE_LOOK) != true
        }

      override fun hideToolWindow() {
        toolWindow.toolWindowManager.hideToolWindow(id = toolWindow.id, source = ToolWindowEventSource.HideButton)
      }
    }
    enableEvents(AWTEvent.COMPONENT_EVENT_MASK)
    installFocusTraversalPolicy(this, LayoutFocusTraversalPolicy())
    dividerAndHeader.isOpaque = false
    dividerAndHeader.add(JBUI.Panels.simplePanel(header).addToBottom(notificationHeader), BorderLayout.SOUTH)
    if (SystemInfoRt.isMac) {
      background = JBColor(Gray._200, Gray._90)
    }
    if (toolWindow.toolWindowManager.isNewUi) {
      background = JBUI.CurrentTheme.ToolWindow.background()
    }
    contentManager.addContentManagerListener(object : ContentManagerListener {
      override fun contentRemoved(event: ContentManagerEvent) {
        val parentDecorator = findNearestDecorator(this@InternalDecoratorImpl) ?: return
        if (!parentDecorator.isSplitUnsplitInProgress() && !isSplitUnsplitInProgress() && contentManager.isEmpty) {
          parentDecorator.unsplit(null)
        }
      }
    })
  }

  fun updateMode(mode: Mode) {
    if (mode == this.mode) {
      return
    }

    this.mode = mode
    removeAll()
    border = null
    when (mode) {
      Mode.SINGLE, Mode.CELL -> {
        layout = BorderLayout()

        val decorator = InternalUICustomization.getInstance()?.toolWindowUIDecorator
          ?.decorateAndReturnHolder(dividerAndHeader, myDecoratorChild, toolWindow) { InnerPanelBorder(toolWindow) }

        decorator?.let {
          add(it, BorderLayout.CENTER)
        } ?: run {
          add(dividerAndHeader, BorderLayout.NORTH)
          add(myDecoratorChild, BorderLayout.CENTER)
          border = InnerPanelBorder(toolWindow)
        }

        firstDecorator?.let {
          Disposer.dispose(it.contentManager)
        }
        secondDecorator?.let {
          Disposer.dispose(it.contentManager)
        }
        return
      }
      Mode.VERTICAL_SPLIT, Mode.HORIZONTAL_SPLIT -> {
        val splitter = OnePixelSplitter(mode == Mode.VERTICAL_SPLIT)
        splitter.setFirstComponent(firstDecorator)
        splitter.setSecondComponent(secondDecorator)
        this.splitter = splitter
        layout = BorderLayout()
        add(splitter, BorderLayout.CENTER)
      }
    }
  }

  fun splitWithContent(content: Content,
                       @MagicConstant(
                         intValues = [SwingConstants.CENTER.toLong(), SwingConstants.TOP.toLong(), SwingConstants.LEFT.toLong(), SwingConstants.BOTTOM.toLong(), SwingConstants.RIGHT.toLong(), -1]) dropSide: Int,
                       dropIndex: Int) {
    if (dropSide == -1 || dropSide == SwingConstants.CENTER || dropIndex >= 0) {
      contentManager.addContent(content, dropIndex)
      return
    }
    firstDecorator = toolWindow.createCellDecorator().also {
      it.setTabActions(tabActions)
      it.setTitleActions(titleActions)
    }
    attach(firstDecorator)
    secondDecorator = toolWindow.createCellDecorator().also {
      it.setTabActions(tabActions)
      it.setTitleActions(titleActions)
    }
    attach(secondDecorator)
    val contents = contentManager.contents.toMutableList()
    if (!contents.contains(content)) {
      contents.add(content)
    }
    for (c in contents) {
      moveContent(c, this,
                  (if ((c !== content) xor (dropSide == SwingConstants.LEFT || dropSide == SwingConstants.TOP)) firstDecorator else secondDecorator)!!)
    }
    firstDecorator!!.updateMode(Mode.CELL)
    secondDecorator!!.updateMode(Mode.CELL)
    updateMode(if (dropSide == SwingConstants.TOP || dropSide == SwingConstants.BOTTOM) Mode.VERTICAL_SPLIT else Mode.HORIZONTAL_SPLIT)
  }

  private fun raise(raiseFirst: Boolean) {
    val source = if (raiseFirst) firstDecorator!! else secondDecorator!!
    val first = source.firstDecorator
    val second = source.secondDecorator
    val mode = source.mode
    source.detach(first)
    source.detach(second)
    source.firstDecorator = null
    source.secondDecorator = null
    val toRemove1 = firstDecorator
    val toRemove2 = secondDecorator
    toRemove1!!.updateMode(Mode.CELL)
    toRemove2!!.updateMode(Mode.CELL)
    first!!.setSplitUnsplitInProgress(true)
    second!!.setSplitUnsplitInProgress(true)
    try {
      firstDecorator = first
      secondDecorator = second
      this.mode = mode //Previous mode is split too
      splitter!!.orientation = mode == Mode.VERTICAL_SPLIT
      splitter!!.firstComponent = firstDecorator
      splitter!!.secondComponent = secondDecorator
      attach(first)
      attach(second)
    }
    finally {
      first.setSplitUnsplitInProgress(false)
      second.setSplitUnsplitInProgress(false)
      Disposer.dispose(toRemove1.contentManager)
      Disposer.dispose(toRemove2.contentManager)
    }
  }

  private fun detach(decorator: InternalDecoratorImpl?) {
    val parentManager = contentManager
    val childManager = decorator!!.contentManager
    if (parentManager is ContentManagerImpl && childManager is ContentManagerImpl) {
      parentManager.removeNestedManager((childManager as ContentManagerImpl?)!!)
    }
  }

  private fun attach(decorator: InternalDecoratorImpl?) {
    val parentManager = contentManager
    val childManager = decorator!!.contentManager
    if (parentManager is ContentManagerImpl && childManager is ContentManagerImpl) {
      parentManager.addNestedManager(childManager)
    }
  }

  fun canUnsplit(): Boolean {
    if (mode != Mode.CELL) return false
    val parent = findNearestDecorator(this)
    if (parent != null) {
      if (parent.firstDecorator == this) {
        return parent.secondDecorator != null && parent.secondDecorator!!.mode == Mode.CELL
      }
      if (parent.secondDecorator == this) {
        return parent.firstDecorator != null && parent.firstDecorator!!.mode == Mode.CELL
      }
    }
    return false
  }

  fun unsplit(toSelect: Content?) {
    if (!mode!!.isSplit) {
      findNearestDecorator(this)?.unsplit(toSelect)
      return
    }
    if (isSplitUnsplitInProgress()) {
      return
    }
    setSplitUnsplitInProgress(true)
    try {
      when {
        firstDecorator == null || secondDecorator == null -> {
          return
        }
        firstDecorator!!.mode!!.isSplit -> {
          raise(true)
          return
        }
        secondDecorator!!.mode!!.isSplit -> {
          raise(false)
          return
        }
        else -> {
          for (c in firstDecorator!!.contentManager.contents) {
            moveContent(c, firstDecorator!!, this)
          }
          for (c in secondDecorator!!.contentManager.contents) {
            moveContent(c, secondDecorator!!, this)
          }
          updateMode(if (findNearestDecorator(this) != null) Mode.CELL else Mode.SINGLE)
          toSelect?.manager?.setSelectedContent(toSelect)
          firstDecorator = null
          secondDecorator = null
          splitter = null
        }
      }
    }
    finally {
      setSplitUnsplitInProgress(false)
    }
  }

  fun setSplitUnsplitInProgress(inProgress: Boolean) {
    isSplitUnsplitInProgress = inProgress
  }

  override fun isSplitUnsplitInProgress(): Boolean = isSplitUnsplitInProgress

  override fun getContentManager(): ContentManager = contentUi.contentManager

  override fun getHeaderToolbar(): ActionToolbar = header.getToolbar()

  val headerToolbarActions: ActionGroup
    get() = header.getToolbarActions()
  val tabToolbarActions: ActionGroup
    get() = contentUi.tabToolbarActions

  override fun toString(): String {
    return toolWindow.id + ": " + StringUtil.trimMiddle(contentManager.contents.joinToString { it.displayName ?: "null" }, 40) +
           " #" + System.identityHashCode(this)
  }

  private fun initDivider(): JComponent {
    divider?.let {
      return it
    }
    divider = object : JPanel() {
      override fun getCursor(): Cursor {
        val info = toolWindow.windowInfo
        val isVerticalCursor = if (info.type == ToolWindowType.DOCKED) info.anchor.isSplitVertically else info.anchor.isHorizontal
        return if (isVerticalCursor) Cursor.getPredefinedCursor(Cursor.S_RESIZE_CURSOR)
        else Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR)
      }
    }
    return divider!!
  }

  override fun doLayout() {
    super.doLayout()
    initDivider().bounds = when (toolWindow.anchor) {
      ToolWindowAnchor.TOP -> Rectangle(0, height - 1, width, 0)
      ToolWindowAnchor.LEFT -> Rectangle(width - 1, 0, 0, height)
      ToolWindowAnchor.BOTTOM -> Rectangle(0, 0, width, 0)
      ToolWindowAnchor.RIGHT -> Rectangle(0, 0, 0, height)
      else -> Rectangle(0, 0, 0, 0)
    }
  }

  fun applyWindowInfo(info: WindowInfo) {
    if (info.type == ToolWindowType.SLIDING) {
      val anchor = info.anchor
      if (log().isDebugEnabled) {
        log().debug("The sliding window ${info.id} anchor is now $anchor")
      }
      val divider = initDivider()
      divider.invalidate()
      when (anchor) {
        ToolWindowAnchor.TOP -> add(divider, BorderLayout.SOUTH)
        ToolWindowAnchor.LEFT -> add(divider, BorderLayout.EAST)
        ToolWindowAnchor.BOTTOM -> dividerAndHeader.add(divider, BorderLayout.NORTH)
        ToolWindowAnchor.RIGHT -> add(divider, BorderLayout.WEST)
      }
      divider.preferredSize = Dimension(0, 0)
    }
    else if (divider != null) {
      if (log().isDebugEnabled) {
        log().debug("Removing divider of the non-sliding (${info.type}) window ${info.id}")
      }
      // docked and floating windows don't have divider
      divider!!.parent?.remove(divider)
      divider = null
    }

    // push "apply" request forward
    if (!info.type.isInternal) {
      getExternalDecorator(info.type)?.apply(info)
    }
  }

  internal fun getExternalDecorator(type: ToolWindowType): ToolWindowExternalDecorator? {
    var result: ToolWindowExternalDecorator? = null
    var component: Component? = this
    while (component != null) {
      result = ClientProperty.get(component, ToolWindowExternalDecorator.DECORATOR_PROPERTY)
      if (result != null && result.getToolWindowType() == type) {
        break
      }
      component = component.parent
    }
    return result
  }

  override fun uiDataSnapshot(sink: DataSink) {
    sink[PlatformDataKeys.TOOL_WINDOW] = toolWindow
  }

  fun setTitleActions(actions: List<AnAction>) {
    titleActions.clear()
    titleActions.addAll(actions)
    header.setAdditionalTitleActions(titleActions)
    firstDecorator?.setTitleActions(actions)
    secondDecorator?.setTitleActions(actions)
  }

  fun setTabActions(actions: List<AnAction>) {
    tabActions = actions
    contentUi.setTabActions(actions)
    firstDecorator?.setTabActions(actions)
    secondDecorator?.setTabActions(actions)
  }

  private inner class InnerPanelBorder(private val window: ToolWindowImpl) : Border {

    private var paintLeftExternalBorder = false
    private var paintLeftInternalBorder = false
    private var paintRightExternalBorder = false
    private var paintRightInternalBorder = false

    override fun paintBorder(c: Component, g: Graphics, x: Int, y: Int, width: Int, height: Int) {
      doPaintBorder(c, g, x, y, width, height)
    }

    private fun doPaintBorder(c: Component, g: Graphics, x: Int, y: Int, width: Int, height: Int) {
      val insets = getBorderInsets(c)
      g as Graphics2D
      doPaintExternalBorder(g, insets, x, y, width, height)
      doPaintInternalBorder(g, insets, x, y, height, width)
    }

    private fun doPaintExternalBorder(
      g: Graphics2D,
      insets: Insets,
      x: Int,
      y: Int,
      width: Int,
      height: Int,
      ) {
      if (insets.top > 0) {
        val anchor = window.windowInfo.anchor
        if (anchor == ToolWindowAnchor.BOTTOM || (anchor == ToolWindowAnchor.RIGHT && window.windowInfo.isSplit)) {
          g.color = JBUI.CurrentTheme.ToolWindow.mainBorderColor()
        }
        else {
          g.color = JBUI.CurrentTheme.MainToolbar.borderColor()
        }
        LinePainter2D.paint(g, x.toDouble(), (y + insets.top - 1).toDouble(), (x + width - 1).toDouble(),
                            (y + insets.top - 1).toDouble())
        LinePainter2D.paint(g, x.toDouble(), (y + insets.top).toDouble(), (x + width - 1).toDouble(), (y + insets.top).toDouble())
      }
      g.color = JBUI.CurrentTheme.ToolWindow.mainBorderColor()
      if (paintLeftExternalBorder) {
        LinePainter2D.paint(g, (x - 1).toDouble(), y.toDouble(), (x - 1).toDouble(), (y + height).toDouble())
        LinePainter2D.paint(g, x.toDouble(), y.toDouble(), x.toDouble(), (y + height).toDouble())
      }
      if (paintRightExternalBorder) {
        LinePainter2D.paint(g, (x + width - 1).toDouble(), (y + insets.top).toDouble(), (x + width - 1).toDouble(),
                            (y + height).toDouble())
        LinePainter2D.paint(g, (x + width).toDouble(), (y + insets.top).toDouble(), (x + width).toDouble(),
                            (y + height).toDouble())
      }
      if (insets.bottom > 0) {
        LinePainter2D.paint(g, x.toDouble(), (y + height - 1).toDouble(), (x + width).toDouble(), (y + height - 1).toDouble())
        LinePainter2D.paint(g, x.toDouble(), (y + height).toDouble(), (x + width).toDouble(), (y + height).toDouble())
      }
    }

    private fun doPaintInternalBorder(
      g: Graphics2D,
      insets: Insets,
      x: Int,
      y: Int,
      height: Int,
      width: Int,
    ) {
      g.color = JBUI.CurrentTheme.ToolWindow.background()
      if (paintLeftInternalBorder) {
        val offset = if (paintLeftExternalBorder) 1 else 0
        LinePainter2D.paint(g, (x + offset).toDouble(), y.toDouble(), (x + offset).toDouble(), (y + height).toDouble())
      }
      if (paintRightInternalBorder) {
        val offset = if (paintRightExternalBorder) 2 else 1
        LinePainter2D.paint(g, (x + width - offset).toDouble(), (y + insets.top).toDouble(), (x + width - offset).toDouble(),
                            (y + height).toDouble())
      }
    }

    override fun getBorderInsets(c: Component): Insets {
      val toolWindowManager = window.toolWindowManager
      val windowInfo = window.windowInfo
      if (toolWindowManager.project.isDisposed ||
          !toolWindowManager.isToolWindowRegistered(window.id) ||
          window.isDisposed || !windowInfo.type.isInternal) {
        return JBInsets.emptyInsets()
      }
      val anchor = windowInfo.anchor
      val sideProperties = getSideProperties(c)
      val top = if (SideProperty.TOP_DIVIDER !in sideProperties && toolWindow.type != ToolWindowType.FLOATING &&
                    SideProperty.TOP_TOOL_WINDOW_EDGE in sideProperties) 1 else 0
      val bottom = 0
      var left = if (SideProperty.LEFT_DIVIDER !in sideProperties && anchor == ToolWindowAnchor.RIGHT &&
                     SideProperty.LEFT_TOOL_WINDOW_EDGE in sideProperties) 1 else 0
      var right = if (SideProperty.RIGHT_DIVIDER !in sideProperties && anchor == ToolWindowAnchor.LEFT &&
                      SideProperty.RIGHT_TOOL_WINDOW_EDGE in sideProperties) 1 else 0
      paintLeftExternalBorder = left > 0
      paintRightExternalBorder = right > 0
      paintLeftInternalBorder = false
      paintRightInternalBorder = false

      var component: Component = window.component
      var parent = component.parent
      var isSplitter = false
      var isFirstInSplitter = false
      var isVerticalSplitter = false
      var otherDecoratorInSplitter: InternalDecoratorImpl? = null
      while (parent != null) {
        if (parent is Splitter) {
          val splitter = parent
          isSplitter = true
          isFirstInSplitter = splitter.firstComponent === component
          otherDecoratorInSplitter = (if (isFirstInSplitter) splitter.secondComponent else splitter.firstComponent) as? InternalDecoratorImpl?
          isVerticalSplitter = splitter.isVertical
          break
        }
        component = parent
        parent = component.parent
      }
      val isTouchingTheEditor = when (anchor) {
        ToolWindowAnchor.RIGHT -> !isSplitter || isVerticalSplitter || isFirstInSplitter
        ToolWindowAnchor.LEFT -> !isSplitter || isVerticalSplitter || !isFirstInSplitter
        else -> false
      }
      if (JBColor.border() == EditorColorsManager.getInstance().globalScheme.defaultBackground) {
        // Might need another border if the tool window has an editor-like component touching the corresponding edge.
        // Five cases overall:
        // 1. The tool window on the right touches the editor and has the same background on the left edge.
        // 2. The tool window on the right touches another tool window (side-by-side) and they both have editor backgrounds along that edge.
        // 3. The tool window on the left touches the editor and has the same background on the right edge.
        // 4. The tool window on the left touches another tool window (side-by-side) and they both have editor backgrounds along that edge.
        // 5. Two tool windows on the bottom (in a splitter) have editor background along the common edge.
        // In the 5th case we draw borders on the right side of the divider. The choice between left and right is random, but drawing both looks ugly.
        if ((
            anchor == ToolWindowAnchor.RIGHT &&
            hasEditorLikeComponentOnTheLeft() &&
            (isTouchingTheEditor || otherDecoratorInSplitter?.hasEditorLikeComponentOnTheRight() == true) // cases 1 & 2
          ) || (
            anchor == ToolWindowAnchor.BOTTOM &&
            hasEditorLikeComponentOnTheLeft() &&
            isSplitter &&
            !isFirstInSplitter &&
            otherDecoratorInSplitter?.hasEditorLikeComponentOnTheRight() == true // case 5
        )) {
          ++left
          paintLeftInternalBorder = true
        }
        if (
            anchor == ToolWindowAnchor.LEFT &&
            hasEditorLikeComponentOnTheRight() &&
            (isTouchingTheEditor || otherDecoratorInSplitter?.hasEditorLikeComponentOnTheLeft() == true) // cases 3 & 4
        ) {
          ++right
          paintRightInternalBorder = true
        }
      }
      return Insets(top, left, bottom, right)
    }

    override fun isBorderOpaque(): Boolean {
      return false
    }
  }

  /**
   * Determines what sides of the component [c] are adjacent to dividers and what sides touch the edges of the tool window.
   */
  private fun getSideProperties(c: Component): Set<SideProperty> {
    val result = EnumSet.of(SideProperty.LEFT_TOOL_WINDOW_EDGE, SideProperty.RIGHT_TOOL_WINDOW_EDGE,
                            SideProperty.TOP_TOOL_WINDOW_EDGE, SideProperty.BOTTOM_TOOL_WINDOW_EDGE)
    var component = c
    var reachedToolWindow = component == toolWindow.decorator
    var parent = component.parent
    while (parent != null) {
      if (parent == toolWindow.decorator) {
        reachedToolWindow = true
      }

      when (parent) {
        is Splitter -> {
          val splitter = parent
          if (splitter.isVertical) {
            if (component == splitter.firstComponent) {
              result.add(SideProperty.BOTTOM_DIVIDER)
              if (!reachedToolWindow) {
                result.remove(SideProperty.BOTTOM_TOOL_WINDOW_EDGE)
              }
            } else {
              result.add(SideProperty.TOP_DIVIDER)
              if (!reachedToolWindow) {
                result.remove(SideProperty.TOP_TOOL_WINDOW_EDGE)
              }
            }
          } else if (component == splitter.firstComponent) {
            result.add(SideProperty.RIGHT_DIVIDER)
            if (!reachedToolWindow) {
              result.remove(SideProperty.RIGHT_TOOL_WINDOW_EDGE)
            }
          }
          else {
            result.add(SideProperty.LEFT_DIVIDER)
            if (!reachedToolWindow) {
              result.remove(SideProperty.LEFT_TOOL_WINDOW_EDGE)
            }
          }
        }

        is InternalDecoratorImpl -> {}

        else -> break
      }
      component = parent
      parent = component.parent
    }

    return result
  }

  private fun hasEditorLikeComponentOnTheLeft(): Boolean = componentsWithEditorLikeBackground.any {
    // 2px used as the maximum border width, to avoid the chicken-and-egg dependency:
    // 1. We need to check if there's an editor touching the left edge to calculate the border width.
    // 2. We need to know the border width to figure out what exactly is the left edge.
    // So we just assume everything that's small enough is nothing but a border.
    // In the worst case we'll just paint an extra border when it's not really needed.
    it.locationRelativeToDecorator.x <= 2
  }

  private fun hasEditorLikeComponentOnTheRight(): Boolean = componentsWithEditorLikeBackground.any {
    it.locationRelativeToDecorator.x + width >= this@InternalDecoratorImpl.width - 2
  }

  private val Component.locationRelativeToDecorator: Point
    get() = location.also { point ->
      SwingUtilities.convertPoint(parent, point, this@InternalDecoratorImpl)
    }

  override fun getHeaderHeight(): Int {
    return header.preferredSize.height
  }

  override fun setHeaderVisible(value: Boolean) {
    header.isVisible = value
  }

  override fun isHeaderVisible(): Boolean {
    return header.isVisible
  }

  val isActive: Boolean
    get() = toolWindow.isActive

  fun updateActiveAndHoverState() {
    val isHoverAlphaAnimationEnabled =
      toolWindow.toolWindowManager.isNewUi &&
      !AdvancedSettings.getBoolean("ide.always.show.tool.window.header.icons") &&
      toolWindow.component.getClientProperty(ToolWindowContentUi.DONT_HIDE_TOOLBAR_IN_HEADER) != true
    val narrow = this.toolWindow.decorator?.width?.let { it < JBUI.scale(120) } ?: false
    val isVisible = narrow || !isHoverAlphaAnimationEnabled || isWindowHovered || header.isPopupShowing || toolWindow.isActive

    val toolbar = header.getToolbar()
    if (toolbar is AlphaAnimated) {
      toolbar.alphaContext.isVisible = isVisible
    }

    val tabToolbar = contentUi.tabToolbar
    if (tabToolbar != null && tabToolbar is AlphaAnimated) {
      tabToolbar.alphaContext.isVisible = isVisible
    }
  }

  fun activate(source: ToolWindowEventSource?) {
    toolWindow.fireActivated(source!!)
  }

  val toolWindowId: String
    get() = toolWindow.id

  var headerComponent: JComponent?
    get() {
      val component = notificationHeader.targetComponent
      return if (component !== notificationHeader) component else null
    }
    set(notification) {
      notificationHeader.setContent(notification)
    }

  val headerScreenBounds: Rectangle?
    get() {
      if (!header.isShowing) {
        return null
      }
      val bounds = header.bounds
      bounds.location = header.locationOnScreen
      return bounds
    }

  override fun addNotify() {
    if (log().isTraceEnabled) {
      log().trace(Throwable("Tool window $toolWindowId shown"))
    }
    super.addNotify()
    if (isSplitUnsplitInProgress()) {
      return
    }

    disposable?.let {
      Disposer.dispose(it)
    }

    val divider = divider
    val disposable = Disposer.newCheckedDisposable()
    this.disposable = disposable
    HOVER_STATE_LISTENER.addTo(this, disposable)
    updateActiveAndHoverState()
    if (divider != null) {
      val glassPane = rootPane.glassPane as IdeGlassPane
      val listener = ResizeOrMoveDocketToolWindowMouseListener(divider, glassPane, this)
      glassPane.addMouseMotionPreprocessor(listener, disposable)
      glassPane.addMousePreprocessor(listener, disposable)
    }
    contentUi.update()

    if ((toolWindow.type == ToolWindowType.WINDOWED || toolWindow.type == ToolWindowType.FLOATING) &&
        Registry.`is`("ide.allow.split.and.reorder.in.tool.window")) {
      ToolWindowInnerDragHelper(disposable, this).start()
    }
  }

  override fun removeNotify() {
    if (log().isTraceEnabled) {
      log().trace(Throwable("Tool window $toolWindowId hidden"))
    }
    super.removeNotify()
    if (isSplitUnsplitInProgress()) {
      return
    }

    val disposable = disposable
    if (disposable != null && !disposable.isDisposed) {
      this.disposable = null
      Disposer.dispose(disposable)
    }
  }

  override fun reshape(x: Int, y: Int, w: Int, h: Int) {
    val rectangle = bounds
    super.reshape(x, y, w, h)
    val topLevelDecorator = findTopLevelDecorator(this)
    if (topLevelDecorator != null && topLevelDecorator.isShowing) { // topLevelDecorator != null means that this is not a top level one.
      val hideLabel: Any? = if (SwingUtilities.convertPoint(this, x, y, topLevelDecorator) == Point()) null else "true"
      putClientProperty(ToolWindowContentUi.HIDE_ID_LABEL, hideLabel)
      val topScreenLocation = topLevelDecorator.locationOnScreen
      topScreenLocation.x += topLevelDecorator.width
      val screenLocation = locationOnScreen
      screenLocation.x += w
      val hideButtons = if (topScreenLocation == screenLocation) null else java.lang.Boolean.TRUE
      putClientProperty(HIDE_COMMON_TOOLWINDOW_BUTTONS, hideButtons)
    }
    if (!rectangle.equals(bounds)) {
      contentUi.update()
    }
  }

  fun setDropInfoIndex(index: Int, width: Int) {
    contentUi.setDropInfoIndex(index, width)
  }

  fun updateBounds(dragEvent: MouseEvent) {
    //"Undock" mode only, for "Dock" mode processing see com.intellij.openapi.wm.impl.content.ToolWindowContentUi.initMouseListeners
    val anchor = toolWindow.anchor
    val windowPane = parent
    val lastPoint = SwingUtilities.convertPoint(dragEvent.component, dragEvent.point, windowPane)
    lastPoint.x = MathUtil.clamp(lastPoint.x, 0, windowPane.width)
    lastPoint.y = MathUtil.clamp(lastPoint.y, 0, windowPane.height)
    val bounds = bounds
    when (anchor) {
      ToolWindowAnchor.TOP -> setBounds(0, 0, bounds.width, lastPoint.y)
      ToolWindowAnchor.LEFT -> setBounds(0, 0, lastPoint.x, bounds.height)
      ToolWindowAnchor.BOTTOM -> setBounds(0, lastPoint.y, bounds.width, windowPane.height - lastPoint.y)
      ToolWindowAnchor.RIGHT -> setBounds(lastPoint.x, 0, windowPane.width - lastPoint.x, bounds.height)
    }
    validate()
  }

  private class ResizeOrMoveDocketToolWindowMouseListener(private val divider: JComponent,
                                                          private val glassPane: IdeGlassPane,
                                                          private val decorator: InternalDecoratorImpl) : MouseAdapter() {
    private var isDragging = false
    private fun isInDragZone(e: MouseEvent): Boolean {
      if (!divider.isShowing
          || (divider.width == 0 && divider.height == 0)
          || e.id == MouseEvent.MOUSE_DRAGGED) return false

      val point = SwingUtilities.convertPoint(e.component, e.point, divider)
      val isTopBottom = decorator.toolWindow.windowInfo.anchor.isHorizontal
      val activeArea = Rectangle(divider.size)

      var resizeArea = ToolWindowPane.headerResizeArea
      val target = SwingUtilities.getDeepestComponentAt(e.component, e.point.x, e.point.y)
      if (target is JScrollBar || target is ActionButton) {
        resizeArea /= 3
      }

      if (isTopBottom) {
        activeArea.y -= resizeArea
        activeArea.height += 2 * resizeArea
      }
      else {
        activeArea.x -= resizeArea
        activeArea.width += 2 * resizeArea
      }

      return activeArea.contains(point)
    }

    private fun updateCursor(event: MouseEvent, isInDragZone: Boolean) {
      if (isInDragZone) {
        glassPane.setCursor(divider.cursor, divider)
        event.consume()
      }
    }

    override fun mousePressed(e: MouseEvent) {
      isDragging = isInDragZone(e)
      updateCursor(e, isDragging)
    }

    override fun mouseClicked(e: MouseEvent) {
      updateCursor(e, isInDragZone(e))
    }

    override fun mouseReleased(e: MouseEvent) {
      updateCursor(e, isInDragZone(e))
      isDragging = false
    }

    override fun mouseMoved(e: MouseEvent) {
      updateCursor(e, isDragging || isInDragZone(e))
    }

    override fun mouseDragged(e: MouseEvent) {
      if (!isDragging) {
        return
      }
      decorator.updateBounds(e)
      e.consume()
    }
  }

  override fun putInfo(info: MutableMap<in String, in String>) {
    info["toolWindowTitle"] = toolWindow.title!!
    val selection = toolWindow.contentManager.selectedContent
    if (selection != null) {
      info["toolWindowTab"] = selection.tabName
    }
  }

  override fun getAccessibleContext(): AccessibleContext {
    if (accessibleContext == null) {
      accessibleContext = AccessibleInternalDecorator()
    }
    return accessibleContext
  }

  /** Executes the given action for this and nested decorators. */
  private fun forAllNestedDecorators(action: (InternalDecoratorImpl) -> Unit) {
    action(this)
    firstDecorator?.forAllNestedDecorators(action)
    secondDecorator?.forAllNestedDecorators(action)
  }

  /** Requests focus transfer to the preferred focusable component of the selected content. */
  internal fun requestContentFocus() {
    val component = contentUi.contentManager.selectedContent?.preferredFocusableComponent
    if (component != null && component.isShowing) {
      component.requestFocusInWindow()
    }
  }

  private inner class AccessibleInternalDecorator : AccessibleJPanel() {
    override fun getAccessibleName(): String {
      return super.getAccessibleName()
             ?: (
               ((toolWindow.title?.takeIf(String::isNotEmpty) ?: toolWindow.stripeTitle).takeIf(String::isNotEmpty) ?: toolWindow.id)
               + " " + IdeBundle.message("internal.decorator.accessible.postfix")
                )
    }

    override fun getAccessibleRole(): AccessibleRole {
      return AccessibilityUtils.GROUPED_ELEMENTS
    }
  }

  private enum class SideProperty {
    // Sides adjacent to dividers.
    LEFT_DIVIDER,
    RIGHT_DIVIDER,
    TOP_DIVIDER,
    BOTTOM_DIVIDER,
    // Sides adjacent to the edges of the containing tool window.
    LEFT_TOOL_WINDOW_EDGE,
    RIGHT_TOOL_WINDOW_EDGE,
    TOP_TOOL_WINDOW_EDGE,
    BOTTOM_TOOL_WINDOW_EDGE,
  }

  private fun log(): Logger = toolWindow.toolWindowManager.log()
}
