// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.toolWindow

import com.intellij.ide.IdeBundle
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.ui.Queryable
import com.intellij.openapi.ui.Splitter
import com.intellij.openapi.util.CheckedDisposable
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.wm.IdeGlassPane
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ToolWindowType
import com.intellij.openapi.wm.WindowInfo
import com.intellij.openapi.wm.impl.FloatingDecorator
import com.intellij.openapi.wm.impl.InternalDecorator
import com.intellij.openapi.wm.impl.ToolWindowImpl
import com.intellij.openapi.wm.impl.ToolWindowsPane
import com.intellij.openapi.wm.impl.content.ToolWindowContentUi
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
import com.intellij.util.ObjectUtils
import com.intellij.util.animation.AlphaAnimated
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.JBUI
import org.intellij.lang.annotations.MagicConstant
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls
import java.awt.*
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.util.function.Supplier
import javax.accessibility.AccessibleContext
import javax.swing.*
import javax.swing.border.Border

@ApiStatus.Internal
class InternalDecoratorImpl internal constructor(
  val toolWindow: ToolWindowImpl,
  private val contentUi: ToolWindowContentUi,
  private val myDecoratorChild: JComponent
) : InternalDecorator(), Queryable, DataProvider, ComponentWithMnemonics {
  companion object {
    val SHARED_ACCESS_KEY = Key.create<Boolean>("sharedAccess")

    internal val HIDE_COMMON_TOOLWINDOW_BUTTONS = Key.create<Boolean>("HideCommonToolWindowButtons")
    internal val INACTIVE_LOOK = Key.create<Boolean>("InactiveLook")

    /**
     * Catches all event from tool window and modifies decorator's appearance.
     */
    internal const val HIDE_ACTIVE_WINDOW_ACTION_ID = "HideActiveWindow"

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

    private fun installDefaultFocusTraversalKeys(container: Container, id: Int) {
      container.setFocusTraversalKeys(id, KeyboardFocusManager.getCurrentKeyboardFocusManager().getDefaultFocusTraversalKeys(id))
    }

    private val HOVER_STATE_LISTENER: HoverStateListener = object : HoverStateListener() {
      override fun hoverChanged(component: Component, hovered: Boolean) {
        if (component is InternalDecoratorImpl) {
          val decorator = component
          decorator.isWindowHovered = hovered
          decorator.updateActiveAndHoverState()
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

  init {
    isFocusable = false
    focusTraversalPolicy = LayoutFocusTraversalPolicy()
    updateMode(Mode.SINGLE)
    header = object : ToolWindowHeader(toolWindow, contentUi, Supplier { toolWindow.createPopupGroup(true) }) {
      override val isActive: Boolean
        get() {
          return toolWindow.isActive && ClientProperty.get(this@InternalDecoratorImpl, INACTIVE_LOOK) != true && !ExperimentalUI.isNewUI()
        }

      override fun hideToolWindow() {
        toolWindow.toolWindowManager.hideToolWindow(toolWindow.id, false, true, false, ToolWindowEventSource.HideButton)
      }
    }
    enableEvents(AWTEvent.COMPONENT_EVENT_MASK)
    installFocusTraversalPolicy(this, LayoutFocusTraversalPolicy())
    dividerAndHeader.isOpaque = false
    dividerAndHeader.add(JBUI.Panels.simplePanel(header).addToBottom(notificationHeader), BorderLayout.SOUTH)
    if (SystemInfoRt.isMac) {
      background = JBColor(Gray._200, Gray._90)
    }
    if (ExperimentalUI.isNewUI()) {
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
        add(dividerAndHeader, BorderLayout.NORTH)
        add(myDecoratorChild, BorderLayout.CENTER)
        ApplicationManager.getApplication().invokeLater({ border = InnerPanelBorder(toolWindow) }, toolWindow.project.disposed)
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
    firstDecorator = toolWindow.createCellDecorator()
    attach(firstDecorator)
    secondDecorator = toolWindow.createCellDecorator()
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
      ObjectUtils.consumeIfNotNull(findNearestDecorator(this)) { decorator: InternalDecoratorImpl -> decorator.unsplit(toSelect) }
      return
    }
    if (isSplitUnsplitInProgress()) {
      return
    }
    setSplitUnsplitInProgress(true)
    try {
      if (firstDecorator == null || secondDecorator == null) return
      if (firstDecorator!!.mode!!.isSplit) {
        raise(true)
        return
      }
      if (secondDecorator!!.mode!!.isSplit) {
        raise(false)
        return
      }
      for (c in firstDecorator!!.contentManager.contents) {
        moveContent(c, firstDecorator!!, this)
      }
      for (c in secondDecorator!!.contentManager.contents) {
        moveContent(c, secondDecorator!!, this)
      }
      updateMode(if (findNearestDecorator(this) != null) Mode.CELL else Mode.SINGLE)
      if (toSelect != null) {
        ObjectUtils.consumeIfNotNull(toSelect.manager) { m: ContentManager -> m.setSelectedContent(toSelect) }
      }
      firstDecorator = null
      secondDecorator = null
      splitter = null
    }
    finally {
      setSplitUnsplitInProgress(false)
    }
  }

  fun setSplitUnsplitInProgress(inProgress: Boolean) {
    isSplitUnsplitInProgress = inProgress
  }

  override fun isSplitUnsplitInProgress(): Boolean = isSplitUnsplitInProgress

  override fun getContentManager() = contentUi.contentManager

  override fun getHeaderToolbar() = header.getToolbar()

  val headerToolbarActions: ActionGroup
    get() = header.getToolbarActions()
  val headerToolbarWestActions: ActionGroup
    get() = header.getToolbarWestActions()

  override fun toString(): String {
    return toolWindow.id + ": " + StringUtil.trimMiddle(contentManager.contents.joinToString { it.displayName }, 40) +
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

  fun applyWindowInfo(info: WindowInfo) {
    if (info.type == ToolWindowType.SLIDING) {
      val anchor = info.anchor
      val divider = initDivider()
      divider.invalidate()
      if (anchor == ToolWindowAnchor.TOP) {
        add(divider, BorderLayout.SOUTH)
      }
      else if (anchor == ToolWindowAnchor.LEFT) {
        add(divider, BorderLayout.EAST)
      }
      else if (anchor == ToolWindowAnchor.BOTTOM) {
        dividerAndHeader.add(divider, BorderLayout.NORTH)
      }
      else if (anchor == ToolWindowAnchor.RIGHT) {
        add(divider, BorderLayout.WEST)
      }
      divider.preferredSize = Dimension(0, 0)
    }
    else if (divider != null) {
      // docked and floating windows don't have divider
      divider!!.parent.remove(divider)
      divider = null
    }

    // push "apply" request forward
    if (info.type == ToolWindowType.FLOATING) {
      val floatingDecorator = SwingUtilities.getAncestorOfClass(FloatingDecorator::class.java, this) as FloatingDecorator
      floatingDecorator.apply(info)
    }
  }

  override fun getData(dataId: @NonNls String): Any? {
    return if (PlatformDataKeys.TOOL_WINDOW.`is`(dataId)) toolWindow else null
  }

  public override fun processKeyBinding(ks: KeyStroke, e: KeyEvent, condition: Int, pressed: Boolean): Boolean {
    if (condition == WHEN_ANCESTOR_OF_FOCUSED_COMPONENT && pressed) {
      val keyStrokes = KeymapUtil.getKeyStrokes(ActionManager.getInstance().getAction("FocusEditor").shortcutSet)
      if (keyStrokes.contains(ks)) {
        toolWindow.toolWindowManager.activateEditorComponent()
        return true
      }
    }
    return super.processKeyBinding(ks, e, condition, pressed)
  }

  fun setTitleActions(actions: List<AnAction>) {
    header.setAdditionalTitleActions(actions)
  }

  fun setTabActions(actions: List<AnAction>) {
    header.setTabActions(actions)
  }

  private class InnerPanelBorder(private val window: ToolWindowImpl) : Border {
    override fun paintBorder(c: Component, g: Graphics, x: Int, y: Int, width: Int, height: Int) {
      g.color = JBColor.border()
      doPaintBorder(c, g, x, y, width, height)
    }

    private fun doPaintBorder(c: Component, g: Graphics, x: Int, y: Int, width: Int, height: Int) {
      val insets = getBorderInsets(c)
      val graphics2D = g as Graphics2D
      if (insets.top > 0) {
        LinePainter2D.paint(graphics2D, x.toDouble(), (y + insets.top - 1).toDouble(), (x + width - 1).toDouble(),
                            (y + insets.top - 1).toDouble())
        LinePainter2D.paint(graphics2D, x.toDouble(), (y + insets.top).toDouble(), (x + width - 1).toDouble(), (y + insets.top).toDouble())
      }
      if (insets.left > 0) {
        LinePainter2D.paint(graphics2D, x.toDouble(), y.toDouble(), x.toDouble(), (y + height).toDouble())
        LinePainter2D.paint(graphics2D, (x + 1).toDouble(), y.toDouble(), (x + 1).toDouble(), (y + height).toDouble())
      }
      if (insets.right > 0) {
        LinePainter2D.paint(graphics2D, (x + width - 1).toDouble(), (y + insets.top).toDouble(), (x + width - 1).toDouble(),
                            (y + height).toDouble())
        LinePainter2D.paint(graphics2D, (x + width).toDouble(), (y + insets.top).toDouble(), (x + width).toDouble(),
                            (y + height).toDouble())
      }
      if (insets.bottom > 0) {
        LinePainter2D.paint(graphics2D, x.toDouble(), (y + height - 1).toDouble(), (x + width).toDouble(), (y + height - 1).toDouble())
        LinePainter2D.paint(graphics2D, x.toDouble(), (y + height).toDouble(), (x + width).toDouble(), (y + height).toDouble())
      }
    }

    override fun getBorderInsets(c: Component): Insets {
      val toolWindowManager = window.toolWindowManager
      val windowInfo = window.windowInfo
      if (toolWindowManager.project.isDisposed ||
          !toolWindowManager.isToolWindowRegistered(window.id) ||
          window.isDisposed || windowInfo.type == ToolWindowType.FLOATING || windowInfo.type == ToolWindowType.WINDOWED) {
        return JBInsets.emptyInsets()
      }
      val anchor = windowInfo.anchor
      var component: Component = window.component
      var parent = component.parent
      var isSplitter = false
      var isFirstInSplitter = false
      var isVerticalSplitter = false
      while (parent != null) {
        if (parent is Splitter) {
          val splitter = parent
          isSplitter = true
          isFirstInSplitter = splitter.firstComponent === component
          isVerticalSplitter = splitter.isVertical
          break
        }
        component = parent
        parent = component.getParent()
      }
      val top = if (isSplitter && (anchor == ToolWindowAnchor.RIGHT || anchor == ToolWindowAnchor.LEFT) && windowInfo.isSplit && isVerticalSplitter) -1 else 0
      val left = if (anchor == ToolWindowAnchor.RIGHT && (!isSplitter || isVerticalSplitter || isFirstInSplitter)) 1 else 0
      val bottom = 0
      val right = if (anchor == ToolWindowAnchor.LEFT && (!isSplitter || isVerticalSplitter || !isFirstInSplitter)) 1 else 0
      return Insets(top, left, bottom, right)
    }

    override fun isBorderOpaque(): Boolean {
      return false
    }
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
    val toolbar = headerToolbar
    if (toolbar is AlphaAnimated) {
      val alpha = toolbar as AlphaAnimated
      alpha.alphaAnimator.setVisible(!ExperimentalUI.isNewUI() || isWindowHovered || header.isPopupShowing || toolWindow.isActive)
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
      if (!header.isShowing) return null
      val bounds = header.bounds
      bounds.location = header.locationOnScreen
      return bounds
    }

  override fun addNotify() {
    super.addNotify()
    if (isSplitUnsplitInProgress()) {
      return
    }
    if (disposable != null) {
      Disposer.dispose(disposable!!)
    }
    val divider = divider
    disposable = Disposer.newCheckedDisposable()
    HOVER_STATE_LISTENER.addTo(this, disposable!!)
    updateActiveAndHoverState()
    if (divider != null) {
      val glassPane = rootPane.glassPane as IdeGlassPane
      val listener = ResizeOrMoveDocketToolWindowMouseListener(divider, glassPane, this)
      glassPane.addMouseMotionPreprocessor(listener, disposable!!)
      glassPane.addMousePreprocessor(listener, disposable!!)
    }
    contentUi.update()
  }

  override fun removeNotify() {
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
    super.reshape(x, y, w, h)
    val topLevelDecorator = findTopLevelDecorator(this)
    if (topLevelDecorator == null || !topLevelDecorator.isShowing) {
      putClientProperty(ToolWindowContentUi.HIDE_ID_LABEL, null)
      putClientProperty(HIDE_COMMON_TOOLWINDOW_BUTTONS, null)
      putClientProperty(INACTIVE_LOOK, null)
    }
    else {
      val hideLabel: Any? = if (SwingUtilities.convertPoint(this, x, y, topLevelDecorator) == Point()) null else "true"
      putClientProperty(ToolWindowContentUi.HIDE_ID_LABEL, hideLabel)
      val topScreenLocation = topLevelDecorator.locationOnScreen
      topScreenLocation.x += topLevelDecorator.width
      val screenLocation = locationOnScreen
      screenLocation.x += w
      val hideButtons = if (topScreenLocation == screenLocation) null else java.lang.Boolean.TRUE
      val hideActivity = if (topScreenLocation.y == screenLocation.y) null else java.lang.Boolean.TRUE
      putClientProperty(HIDE_COMMON_TOOLWINDOW_BUTTONS, hideButtons)
      putClientProperty(INACTIVE_LOOK, hideActivity)
    }
    contentUi.update()
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
    if (anchor == ToolWindowAnchor.TOP) {
      setBounds(0, 0, bounds.width, lastPoint.y)
    }
    else if (anchor == ToolWindowAnchor.LEFT) {
      setBounds(0, 0, lastPoint.x, bounds.height)
    }
    else if (anchor == ToolWindowAnchor.BOTTOM) {
      setBounds(0, lastPoint.y, bounds.width, windowPane.height - lastPoint.y)
    }
    else if (anchor == ToolWindowAnchor.RIGHT) {
      setBounds(lastPoint.x, 0, windowPane.width - lastPoint.x, bounds.height)
    }
    validate()
  }

  private class ResizeOrMoveDocketToolWindowMouseListener(private val divider: JComponent,
                                                          private val glassPane: IdeGlassPane,
                                                          private val decorator: InternalDecoratorImpl) : MouseAdapter() {
    private var isDragging = false
    private fun isInDragZone(e: MouseEvent): Boolean {
      val point = Point(e.point)
      SwingUtilities.convertPointToScreen(point, e.component)
      if ((if (decorator.toolWindow.windowInfo.anchor.isHorizontal) point.y else point.x) == 0) {
        return false
      }
      SwingUtilities.convertPointFromScreen(point, divider)
      return Math.abs(
        if (decorator.toolWindow.windowInfo.anchor.isHorizontal) point.y else point.x) <= ToolWindowsPane.getHeaderResizeArea()
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

  private inner class AccessibleInternalDecorator : AccessibleJPanel() {
    override fun getAccessibleName(): String {
      return super.getAccessibleName()
             ?: (
               ((toolWindow.title?.takeIf(String::isNotEmpty) ?: toolWindow.stripeTitle).takeIf(String::isNotEmpty) ?: toolWindow.id ?: "")
               + " " + IdeBundle.message("internal.decorator.accessible.postfix")
                )
    }
  }
}