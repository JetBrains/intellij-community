// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.tabs

import com.intellij.icons.AllIcons
import com.intellij.ide.IdeBundle
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.impl.InternalUICustomization
import com.intellij.openapi.components.ComponentManagerEx
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.AbstractPainter
import com.intellij.openapi.ui.popup.util.PopupUtil
import com.intellij.openapi.util.ActionCallback
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.openapi.wm.IdeGlassPaneUtil
import com.intellij.openapi.wm.impl.IdeFrameImpl
import com.intellij.ui.ClientProperty
import com.intellij.ui.ColorUtil
import com.intellij.ui.ComponentUtil
import com.intellij.ui.ExperimentalUI
import com.intellij.ui.IconManager
import com.intellij.ui.InplaceButton
import com.intellij.ui.IslandsState
import com.intellij.ui.JBColor
import com.intellij.ui.PopupMenuListenerAdapter
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.awt.RelativeRectangle
import com.intellij.ui.components.panels.Wrapper
import com.intellij.ui.docking.DockContainer
import com.intellij.ui.docking.DockContainer.ContentResponse
import com.intellij.ui.docking.DockableContent
import com.intellij.ui.docking.DockableContentContainer
import com.intellij.ui.docking.DragSession
import com.intellij.ui.docking.impl.DockManagerImpl
import com.intellij.ui.paint.LinePainter2D
import com.intellij.ui.paint.PaintUtil
import com.intellij.ui.tabs.impl.JBDefaultTabPainter
import com.intellij.ui.tabs.impl.JBTabsImpl
import com.intellij.ui.tabs.impl.MorePopupAware
import com.intellij.ui.tabs.impl.TabLabel
import com.intellij.ui.tabs.impl.TabLayout
import com.intellij.ui.tabs.impl.TabPainterAdapter
import com.intellij.ui.tabs.impl.singleRow.WindowTabsLayout
import com.intellij.ui.tabs.impl.themes.DefaultTabTheme
import com.intellij.util.ui.GraphicsUtil
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Image
import java.awt.Insets
import java.awt.Rectangle
import java.awt.Shape
import java.awt.event.ActionEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.awt.geom.Rectangle2D
import java.awt.image.BufferedImage
import java.beans.PropertyChangeListener
import java.util.function.BooleanSupplier
import java.util.function.Consumer
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.SwingUtilities
import javax.swing.event.PopupMenuEvent

/**
 * Shows the frames of one native tab group as the project tabs of [ownerFrame].
 * A subclass implements the operations on the native tab group.
 */
@ApiStatus.Internal
abstract class ProjectTabsComponent(
  @JvmField protected val ownerFrame: IdeFrameImpl,
  project: Project?,
  private val tabsDisposable: Disposable,
) : JBTabsImpl(project, tabsDisposable) {
  companion object {
    const val CLOSE_TAB_KEY: String = "CloseTab"

    private const val TITLE_LISTENER_KEY = "TitleListener"

    private const val TAB_HEIGHT = 30

    private var dockManager: DockManagerImpl? = null

    /**
     * Lets the user drop a tab on [frame] to move the tab to the native tab group of [frame].
     * The drop is possible only when [canAcceptTab] returns `true`.
     * [addTab] moves the frame of the dropped tab.
     */
    @JvmStatic
    fun registerFrameDockContainer(
      frame: IdeFrameImpl,
      coroutineScope: CoroutineScope,
      canAcceptTab: BooleanSupplier,
      addTab: Consumer<IdeFrameImpl>,
    ) {
      getDockManager().register(object : DockContainer {
        private var paintDisposable: Disposable? = null

        override fun getAcceptArea(): RelativeRectangle = RelativeRectangle(frame.getComponent())

        override fun getContentResponse(content: DockableContent<*>, point: RelativePoint?): ContentResponse {
          return if (content is WindowFrameDockableContent && !content.isInFullScreen() && canAcceptTab.asBoolean && !frame.isInFullScreen()) {
            ContentResponse.ACCEPT_MOVE
          }
          else {
            ContentResponse.DENY
          }
        }

        override fun getContainerComponent(): JComponent = frame.getComponent()

        override fun startDropOver(content: DockableContent<*>, point: RelativePoint?): Image? {
          val disposable = Disposer.newDisposable("GlassPaneListeners")
          paintDisposable = disposable
          val component = frame.getComponent()
          IdeGlassPaneUtil.find(component).addPainter(component, WindowDropAreaPainter(frame), disposable)
          return null
        }

        override fun resetDropOver(content: DockableContent<*>) {
          paintDisposable?.let { Disposer.dispose(it) }
          paintDisposable = null
        }

        override fun add(content: DockableContent<*>, dropTarget: RelativePoint?) {
          addTab.accept((content as WindowFrameDockableContent).getKey())
        }

        override fun isEmpty(): Boolean = false

        override fun isDisposeWhenEmpty(): Boolean = false
      }, coroutineScope)
    }

    private fun getDockManager(): DockManagerImpl {
      dockManager?.let { return it }
      val project = ProjectManager.getInstance().defaultProject
      return DockManagerImpl(project, (project as ComponentManagerEx).getCoroutineScope()).also { dockManager = it }
    }

    private fun updateDockContainers(manager: DockManagerImpl) {
      for (container in manager.getContainers()) {
        if (container is TabsDockContainer) {
          container.checkEnabled()
        }
      }
    }

    private fun getContentInsets(): Insets {
      return InternalUICustomization.getInstance()?.getProjectTabContentInsets() ?: JBUI.insets(0, 4)
    }

    private fun isSelectionClick(e: MouseEvent): Boolean {
      return e.clickCount == 1 && !e.isPopupTrigger && e.button == MouseEvent.BUTTON1 && !e.isControlDown && !e.isAltDown && !e.isMetaDown
    }

    private fun showCloseActionOnHover(): Boolean = Registry.`is`("ide.mac.os.wintabs.show.closeaction.on.hover", true)

    private fun getFrame(info: TabInfo): IdeFrameImpl = info.`object` as IdeFrameImpl

    @JvmStatic
    fun getPopupFrame(e: AnActionEvent): IdeFrameImpl {
      return getFrame((e.getData(PlatformCoreDataKeys.CONTEXT_COMPONENT) as TabLabel).info)
    }

    private fun removeTitleListener(info: TabInfo) {
      val listener = info.component.getClientProperty(TITLE_LISTENER_KEY) as PropertyChangeListener?
      getFrame(info).removePropertyChangeListener("title", listener)
    }
  }

  private val indexes = HashMap<IdeFrameImpl, Int>()

  init {
    setUiDecorator(object : UiDecorator {
      override fun getDecoration(): UiDecorator.UiDecoration {
        @Suppress("UseDPIAwareInsets")
        return UiDecorator.UiDecoration(
          labelFont = JBFont.medium(),
          labelInsets = Insets(-1, if (dropInfo == null) 0 else -1, -1, -1),
          contentInsetsSupplier = { getContentInsets() },
        )
      }
    })

    Disposer.register(tabsDisposable) {
      for (i in 0 until getTabCount()) {
        removeTitleListener(getTabAt(i))
      }
    }

    createPopupActions()
    installDnD()
  }

  /**
   * Selects [tabFrame] in the native tab group.
   */
  protected abstract fun selectTab(tabFrame: IdeFrameImpl)

  /**
   * Closes [tabFrame] in the native tab group. When [closeOthers] is `true`, closes all other tabs of the group.
   */
  protected abstract fun performCloseTab(tabFrame: IdeFrameImpl, closeOthers: Boolean)

  /**
   * Moves [tabFrame] out of the native tab group to a new window.
   */
  protected abstract fun moveTabToNewWindow(tabFrame: IdeFrameImpl)

  /**
   * Moves [tabFrame] to [index] in its native tab group.
   */
  protected abstract fun moveTabToIndex(tabFrame: IdeFrameImpl, index: Int)

  /**
   * Moves [tabFrame] from another native tab group to [index] in the group of [ownerFrame].
   */
  protected abstract fun insertTab(tabFrame: IdeFrameImpl, index: Int)

  /**
   * Returns `true` when [ownerFrame] is the selected tab of its native tab group.
   */
  protected abstract fun isSelectedInTabGroup(): Boolean

  /**
   * Returns the project tabs component of [frame], or `null` when [frame] shows no project tabs.
   */
  protected abstract fun findTabsComponent(frame: IdeFrameImpl): ProjectTabsComponent?

  override fun getComponentGraphics(graphics: Graphics): Graphics {
    return InternalUICustomization.runGlobalCGTransformWithInactiveFrameSupport(this, graphics)
  }

  fun selfDispose() {
    Disposer.dispose(tabsDisposable)
  }

  override fun createRowLayout(): TabLayout = if (isSingleRow) WindowTabsLayout(this) else super.createRowLayout()

  override fun getPreferredSize(): Dimension = Dimension(super.getPreferredSize().width, JBUI.scale(TAB_HEIGHT))

  override val draggedTabSelectionInfo: TabInfo?
    get() = dragHelper?.dragSource ?: super.draggedTabSelectionInfo

  override fun createTabLabel(info: TabInfo): TabLabel = ProjectTabLabel(info)

  override fun createTabPainterAdapter(): TabPainterAdapter = ProjectTabPainterAdapter()

  fun createTabsForFrame(tabFrames: Array<IdeFrameImpl>) {
    for (tabFrame in tabFrames) {
      createTabItem(tabFrame, -1, tabFrame === ownerFrame)
    }

    recalculateIndexes()

    setSelectionChangeHandler { info, _, doChangeSelection ->
      val tabFrame = getFrame(info)
      if (tabFrame === ownerFrame) {
        if (selectedInfo?.`object` === ownerFrame) ActionCallback.REJECTED else doChangeSelection.run()
      }
      else {
        selectTab(tabFrame)
        ActionCallback.REJECTED
      }
    }
  }

  fun insertTabForFrame(tab: IdeFrameImpl, index: Int) {
    if (indexes.containsKey(tab)) {
      return
    }

    createTabItem(tab, index, false)
    recalculateIndexes()
  }

  /**
   * Removes the tab of [tab]. Returns `true` when only one tab stays after the removal.
   */
  fun removeTabFromFrame(tab: IdeFrameImpl): Boolean {
    for (i in 0 until getTabCount()) {
      val info = getTabAt(i)
      if (info.`object` === tab) {
        removeTitleListener(info)
        removeTab(info)
        recalculateIndexes()
        return getTabCount() == 1
      }
    }
    return false
  }

  private fun isSameGroup(anotherComponent: ProjectTabsComponent): Boolean {
    if (this === anotherComponent) {
      return true
    }

    val count = getTabCount()
    if (count != anotherComponent.getTabCount()) {
      return false
    }

    val frames = HashSet<Any?>()
    val anotherFrames = HashSet<Any?>()

    for (i in 0 until count) {
      frames.add(getTabAt(i).`object`)
      anotherFrames.add(anotherComponent.getTabAt(i).`object`)
    }

    return frames == anotherFrames
  }

  private fun createTabItem(tabFrame: IdeFrameImpl, index: Int, selection: Boolean) {
    val info = TabInfo(JLabel())
    @NlsSafe val tabTitle = tabFrame.title
    info.setObject(tabFrame)
      .setText(tabTitle)
      .setTooltipText(HtmlChunk.text(tabTitle))
    info.setTabLabelActions(createTabActions(tabFrame), ActionPlaces.UNKNOWN)
    info.setDefaultForeground(JBUI.CurrentTheme.MainWindow.Tab.foreground(selection, false))

    addTab(info, index)

    if (selection) {
      select(info, true)

      val windowListener = object : WindowAdapter() {
        override fun windowActivated(e: WindowEvent) {
          repaint()
        }

        override fun windowDeactivated(e: WindowEvent) {
          repaint()
        }
      }
      tabFrame.addWindowListener(windowListener)
      Disposer.register(tabsDisposable) { tabFrame.removeWindowListener(windowListener) }
    }

    val titleListener = PropertyChangeListener { event ->
      val title = event.newValue as String
      info.setText(title).setTooltipText(HtmlChunk.text(title))
    }
    tabFrame.addPropertyChangeListener("title", titleListener)
    info.component.putClientProperty(TITLE_LISTENER_KEY, titleListener)

    installTabDnd(info)
  }

  private fun recalculateIndexes() {
    indexes.clear()

    for (i in 0 until getTabCount()) {
      indexes[getFrame(getTabAt(i))] = i
    }
  }

  private fun reorderTabs(newIndexes: Map<IdeFrameImpl, Int>) {
    sortTabs(Comparator.comparingInt { info -> newIndexes.getValue(getFrame(info)) })
    recalculateIndexes()
  }

  private fun createTabActions(tabFrame: IdeFrameImpl): DefaultActionGroup {
    val closeAction = object : DumbAwareAction(IdeBundle.message("window.tabs.close.title")) {
      override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

      override fun actionPerformed(e: AnActionEvent) {
        closeTab(tabFrame, (e.modifiers and ActionEvent.ALT_MASK) != 0)
      }
    }

    val presentation = closeAction.templatePresentation
    val isNewUiAndDark = ExperimentalUI.isNewUI() && (!JBColor.isBright() || ColorUtil.isDark(JBColor.namedColor("MainToolbar.background")))
    presentation.icon = if (isNewUiAndDark) {
      IconManager.getInstance().getIcon("expui/general/closeSmall_dark.svg", AllIcons::class.java.classLoader)
    }
    else {
      AllIcons.Actions.Close
    }
    presentation.hoveredIcon = if (isNewUiAndDark) {
      IconManager.getInstance().getIcon("expui/general/closeSmallHovered_dark.svg", AllIcons::class.java.classLoader)
    }
    else {
      AllIcons.Actions.CloseHovered
    }

    return DefaultActionGroup(closeAction)
  }

  private fun createPopupActions() {
    val group = DefaultActionGroup()

    fillPopupActions(group)

    setPopupGroup(group, ActionPlaces.POPUP, false)
  }

  protected open fun fillPopupActions(group: DefaultActionGroup) {
    group.add(object : DumbAwareAction(IdeBundle.message("window.tabs.close.tab")) {
      override fun actionPerformed(e: AnActionEvent) {
        closeTab(getPopupFrame(e), false)
      }
    })

    group.add(object : DumbAwareAction(IdeBundle.message("window.tabs.close.other.tabs")) {
      override fun actionPerformed(e: AnActionEvent) {
        closeTab(getPopupFrame(e), true)
      }
    })
  }

  private fun closeTab(tabFrame: IdeFrameImpl, closeOthers: Boolean) {
    tabFrame.rootPane.putClientProperty(CLOSE_TAB_KEY, true)
    performCloseTab(tabFrame, closeOthers)
  }

  private fun moveTabToNewIndex(info: TabInfo) {
    val movedFrame = getFrame(info)
    val newIndex = getIndexOf(info)
    val oldIndex = indexes.getValue(movedFrame)

    if (oldIndex != newIndex) {
      recalculateIndexes()
      moveTabToNewIndex(movedFrame, newIndex)
    }
  }

  private fun moveTabToNewIndex(movedFrame: IdeFrameImpl, newIndex: Int) {
    for (i in 0 until getTabCount()) {
      val tabFrame = getFrame(getTabAt(i))
      if (tabFrame !== ownerFrame) {
        checkNotNull(findTabsComponent(tabFrame)).reorderTabs(indexes)
      }
    }

    if (movedFrame === ownerFrame) {
      moveTabToIndex(movedFrame, newIndex)
    }
    else {
      ApplicationManager.getApplication().invokeLater { moveTabToIndex(movedFrame, newIndex) }
    }
  }

  private fun moveTabToNewIndexOrWindow(info: TabInfo, movedFromOut: Boolean) {
    val movedFrame = getFrame(info)
    val newIndex = dropInfoIndex
    assert(newIndex != -1) { "Wrong Reordering" }

    if (movedFromOut) {
      insertTab(movedFrame, newIndex)
    }
    else {
      info.isHidden = false
      val oldIndex = indexes.getValue(movedFrame)

      if (oldIndex != newIndex) {
        reorderTab(info, newIndex)
        recalculateIndexes()
        moveTabToNewIndex(movedFrame, newIndex)
      }
    }
  }

  private fun installTabDnd(info: TabInfo) {
    info.setDragOutDelegate(object : TabInfo.DragOutDelegate {
      private var session: DragSession? = null

      override fun dragOutStarted(mouseEvent: MouseEvent, info: TabInfo) {
        val content = WindowFrameDockableContent(this@ProjectTabsComponent, info, checkNotNull(getTabLabel(info)))
        info.isHidden = true
        val manager = getDockManager()
        updateDockContainers(manager)
        session = manager.createDragSession(mouseEvent, content)
      }

      override fun processDragOut(event: MouseEvent, source: TabInfo) {
        session?.process(event)
      }

      override fun dragOutFinished(event: MouseEvent, source: TabInfo) {
        session?.process(event)
        session = null
      }

      override fun dragOutCancelled(source: TabInfo) {
        session?.cancel()
        session = null

        source.isHidden = false

        if (getIndexOf(source) != -1) {
          moveTabToNewIndex(source)
        }
      }
    })
    info.dragDelegate = object : TabInfo.DragDelegate {
      override fun dragStarted(mouseEvent: MouseEvent) {
        getTabLabel(info)?.let { setComponentZOrder(it, 0) }
      }

      override fun dragFinishedOrCanceled() {
        selectedInfo?.let { getTabLabel(it) }?.let { setComponentZOrder(it, 0) }
      }
    }
  }

  private fun installDnD() {
    setTabDraggingEnabled(true)

    addListener(object : TabsListener {
      override fun tabsMoved() {
        dragHelper?.dragSource?.let { moveTabToNewIndex(it) }
      }
    })

    getDockManager().register(TabsDockContainer(), tabsDisposable)
  }

  private inner class ProjectTabLabel(info: TabInfo) : TabLabel(this@ProjectTabsComponent, info) {
    init {
      for (listener in mouseListeners) {
        removeMouseListener(listener)
      }
      addMouseListener(object : MouseAdapter() {
        override fun mousePressed(e: MouseEvent) {
          if (!UIUtil.isCloseClick(e, MouseEvent.MOUSE_PRESSED) && !isSelectionClick(e)) {
            handlePopup(e)
          }
        }

        override fun mouseReleased(e: MouseEvent) {
          handlePopup(e)
        }

        override fun mouseClicked(e: MouseEvent) {
          if (isSelectionClick(e)) {
            val component = SwingUtilities.getDeepestComponentAt(e.component, e.x, e.y)
            if (component is InplaceButton) {
              return
            }
            this@ProjectTabsComponent.select(this@ProjectTabLabel.info, true)
            val container = PopupUtil.getPopupContainerFor(this@ProjectTabLabel)
            if (container != null && ClientProperty.isTrue(container.content, MorePopupAware::class.java)) {
              container.cancel()
            }
          }
          else if (e.button == MouseEvent.BUTTON2) {
            closeTab(getFrame(this@ProjectTabLabel.info), false)
          }
          else {
            handlePopup(e)
          }
        }

        override fun mouseEntered(e: MouseEvent) {
          isHovered = true
        }

        override fun mouseExited(e: MouseEvent) {
          isHovered = false
        }
      })
    }

    override fun getDropTargetArc(): Int = JBUI.scale(JBUI.getInt("Island.arc", 10))

    override fun getDropTargetTopOffset(): Int = 0

    override fun getDropTargetBottomOffset(): Int = 0

    override fun getPreferredSize(): Dimension = Dimension(super.getPreferredSize().width, JBUI.scale(TAB_HEIGHT))

    override fun setTabActions(group: ActionGroup?) {
      super.setTabActions(group)
      val panel = actionPanel ?: return
      val parent = panel.parent
      parent.remove(panel)
      parent.add(object : Wrapper(panel) {
        override fun getPreferredSize(): Dimension = panel.preferredSize
      }, BorderLayout.WEST)

      panel.border = JBUI.Borders.emptyLeft(6)
      panel.isVisible = !showCloseActionOnHover()
    }

    override var isHovered: Boolean
      get() = super.isHovered
      set(value) {
        super.isHovered = value
        actionPanel?.isVisible = !showCloseActionOnHover() || value || info === this@ProjectTabsComponent.popupInfo
      }

    override fun handlePopup(e: MouseEvent) {
      super.handlePopup(e)
      val popup = this@ProjectTabsComponent.activePopup ?: return
      popup.addPopupMenuListener(object : PopupMenuListenerAdapter() {
        override fun popupMenuWillBecomeInvisible(e: PopupMenuEvent?) {
          handle()
        }

        override fun popupMenuCanceled(e: PopupMenuEvent?) {
          handle()
        }

        private fun handle() {
          popup.removePopupMenuListener(this)
          actionPanel?.isVisible = !showCloseActionOnHover() || isHovered
        }
      })
    }

    override val isTabActionsOnTheRight: Boolean
      get() = false

    override fun shouldPaintFadeout(): Boolean = false
  }

  private inner class ProjectTabPainterAdapter : TabPainterAdapter {
    override val tabPainter: JBTabPainter = JBDefaultTabPainter(object : DefaultTabTheme() {
      override val background: Color?
        get() = if (IslandsState.isEnabled()) JBColor.namedColor("MainWindow.background", JBColor.PanelBackground) else super.background

      override val topBorderThickness: Int
        get() = 0

      override val inactiveColoredTabBackground: Color?
        get() = null
    })

    override fun paintBackground(label: TabLabel, g: Graphics, tabs: JBTabsImpl) {
      val rect = Rectangle(0, 0, label.width, label.height)
      val info = label.info
      val g2d = g as Graphics2D

      val index = tabs.getIndexOf(info)
      val lastIndex = tabs.getTabCount() - 1
      val border = JBUI.scale(1).toDouble()
      val selected = info.`object` === ownerFrame

      val customization = InternalUICustomization.getInstance()
      if (customization != null && customization.paintProjectTab(ownerFrame, label, g, tabs, selected, index, lastIndex)) {
        return
      }

      if (selected) {
        val window = ComponentUtil.getWindow(this@ProjectTabsComponent)
        val tabColor = JBUI.CurrentTheme.MainWindow.Tab.background(true, window != null && !window.isActive, false)
        tabPainter.paintTab(tabs.tabsPosition, g2d, rect, tabs.borderThickness, tabColor, true, false)

        g.color = JBUI.CurrentTheme.MainWindow.Tab.BORDER
        LinePainter2D.paint(g2d, 0.0, 0.0, rect.width.toDouble(), 0.0, LinePainter2D.StrokeType.INSIDE, border)
        if (index > 0) {
          LinePainter2D.paint(g2d, 0.0, 0.0, 0.0, rect.height.toDouble(), LinePainter2D.StrokeType.INSIDE, border)
        }
        if (index < lastIndex) {
          val x = rect.width - border
          LinePainter2D.paint(g2d, x, 0.0, x, rect.height.toDouble(), LinePainter2D.StrokeType.INSIDE, border)
        }
      }
      else {
        val tabColor = JBUI.CurrentTheme.MainWindow.Tab.background(false, false, label.isHovered)
        tabPainter.paintTab(tabs.tabsPosition, g2d, rect, tabs.borderThickness, tabColor, true, false)

        if (lastIndex > 1 && index < lastIndex && tabs.getTabAt(index + 1) !== tabs.selectedInfo) {
          g.color = JBUI.CurrentTheme.MainWindow.Tab.SEPARATOR
          val x = rect.width - border
          LinePainter2D.paint(g2d, x, 0.0, x, rect.height.toDouble(), LinePainter2D.StrokeType.INSIDE, border)
        }
      }
    }
  }

  private inner class TabsDockContainer : DockContainer {
    private var enabled = false
    private var dropTab: TabInfo? = null
    private var dropImage: Image? = null

    fun checkEnabled() {
      enabled = isSelectedInTabGroup()
    }

    override fun getAcceptArea(): RelativeRectangle = RelativeRectangle(this@ProjectTabsComponent)

    override fun getContentResponse(content: DockableContent<*>, point: RelativePoint?): ContentResponse {
      return if (enabled && content is WindowFrameDockableContent) ContentResponse.ACCEPT_MOVE else ContentResponse.DENY
    }

    override fun getContainerComponent(): JComponent = this@ProjectTabsComponent

    override fun startDropOver(content: DockableContent<*>, point: RelativePoint): Image {
      val tab = TabInfo(JLabel())
        .setText(content.presentation.text)
        .setDefaultForeground(JBUI.CurrentTheme.MainWindow.Tab.foreground(true, false))
      dropTab = tab
      return this@ProjectTabsComponent.startDropOver(tab, point).also { dropImage = it }
    }

    override fun processDropOver(content: DockableContent<*>, point: RelativePoint): Image? {
      dropTab?.let { this@ProjectTabsComponent.processDropOver(it, point) }
      return dropImage
    }

    override fun resetDropOver(content: DockableContent<*>) {
      dropTab?.let { this@ProjectTabsComponent.resetDropOver(it) }
      dropTab = null
      dropImage = null
    }

    override fun add(content: DockableContent<*>, dropTarget: RelativePoint?) {
      val frameContent = content as WindowFrameDockableContent
      moveTabToNewIndexOrWindow(frameContent.info, !isSameGroup(frameContent.tabsComponent))
    }

    override fun isEmpty(): Boolean = false

    override fun isDisposeWhenEmpty(): Boolean = false
  }

  private class WindowFrameDockableContent(
    @JvmField val tabsComponent: ProjectTabsComponent,
    @JvmField val info: TabInfo,
    label: TabLabel,
  ) : DockableContent<IdeFrameImpl>, DockableContentContainer {
    private val size: Dimension = getKey().size
    private val image: BufferedImage = UIUtil.createImage(getKey(), size.width, size.height, BufferedImage.TYPE_INT_ARGB)
    private val presentation = Presentation(info.text)

    init {
      PaintUtil.use(image.createGraphics()) { label.paint(it) }
    }

    override fun getKey(): IdeFrameImpl = getFrame(info)

    fun isInFullScreen(): Boolean = getKey().isInFullScreen() || tabsComponent.ownerFrame.isInFullScreen()

    override fun getPreviewImage(): Image = image

    override fun getDockContainerType(): String? = null

    override fun getPreferredSize(): Dimension = size

    override fun close() {
    }

    override fun getPresentation(): Presentation = presentation

    override fun add(dropTarget: RelativePoint?) {
      tabsComponent.moveTabToNewWindow(getKey())
    }
  }

  private class WindowDropAreaPainter(frame: IdeFrameImpl) : AbstractPainter() {
    private val area: Shape = Rectangle2D.Double(0.0, 0.0, frame.width.toDouble(), frame.height.toDouble())

    override fun needsRepaint(): Boolean = true

    override fun executePaint(component: Component, g: Graphics2D) {
      GraphicsUtil.setupAAPainting(g)
      g.color = JBUI.CurrentTheme.DragAndDrop.Area.BACKGROUND
      g.fill(area)
    }
  }
}
