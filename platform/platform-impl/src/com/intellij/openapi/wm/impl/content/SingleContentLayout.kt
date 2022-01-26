// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.content

import com.intellij.CommonBundle
import com.intellij.icons.AllIcons
import com.intellij.ide.IdeBundle
import com.intellij.ide.actions.PinActiveTabAction
import com.intellij.ide.impl.DataManagerImpl
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.JBPopupMenu
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.openapi.util.*
import com.intellij.openapi.wm.ex.ToolWindowEx
import com.intellij.toolWindow.InternalDecoratorImpl
import com.intellij.ui.ExperimentalUI
import com.intellij.ui.MouseDragHelper
import com.intellij.ui.PopupHandler
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.components.panels.HorizontalLayout
import com.intellij.ui.components.panels.NonOpaquePanel
import com.intellij.ui.content.AlertIcon
import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentManager
import com.intellij.ui.content.impl.ContentManagerImpl
import com.intellij.ui.tabs.*
import com.intellij.ui.tabs.impl.JBTabsImpl
import com.intellij.ui.tabs.impl.MorePopupAware
import com.intellij.ui.tabs.impl.TabLabel
import com.intellij.util.castSafelyTo
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.ui.AbstractLayoutManager
import com.intellij.util.ui.UIUtil
import java.awt.*
import java.awt.event.*
import java.beans.PropertyChangeEvent
import java.beans.PropertyChangeListener
import java.beans.PropertyChangeSupport
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.SwingUtilities

/**
 * Tool window header that shows tabs and actions from its content.
 *
 * If toolwindow [Content] returns [SingleContentSupplier] as a data provider
 * via [SingleContentSupplier.KEY] that in case of single content view
 * all [JBTabs] and actions are moved into this header.
 *
 * When two or more contents exist then header looks like [TabContentLayout].
 */
internal class SingleContentLayout(
  ui: ToolWindowContentUi
) : TabContentLayout(ui) {

  private var isSingleContentView: Boolean = false

  private var tabAdapter: TabAdapter? = null
  private val toolbars = mutableMapOf<ToolbarType, ActionToolbar>()
  private var wrapper: JComponent? = null

  override fun update() {
    super.update()
    tryUpdateContentView()
  }

  override fun rebuild() {
    super.rebuild()
    tryUpdateContentView()
  }

  private fun Content.getSupplier(): SingleContentSupplier? {
    return (component as? DataProvider)?.let(SingleContentSupplier.KEY::getData)
  }

  private fun getSingleContentOrNull(): Content? {
    return findTopLevelContentManager()?.contentsRecursively?.singleOrNull()
  }

  private fun findTopLevelContentManager(): ContentManagerImpl? {
    return InternalDecoratorImpl.findTopLevelDecorator(ui.component)?.contentManager as? ContentManagerImpl
  }

  private fun tryUpdateContentView() {
    val currentContent = getSingleContentOrNull()
    val contentSupplier = currentContent?.getSupplier()

    if (contentSupplier != null) {
      if (isSingleContentView) {
        updateSingleContentView(currentContent, contentSupplier)
      }
      else {
        initSingleContentView(currentContent, contentSupplier)
      }
    }
    else if (isSingleContentView) {
      resetSingleContentView()
    }

    val toolwindow = ui.getWindow().castSafelyTo<ToolWindowEx>()
    if (toolwindow != null) {
      val group = toolwindow.decoration?.actionGroup
      if (isSingleContentView) {
        // install extra actions
        if (group !is ExtendedTitleActionsGroup) {
          toolwindow.setAdditionalGearActions(ExtendedTitleActionsGroup(
            group,
            PinActiveTabAction(),
            Separator.create()
          ))
        }
      }
      else {
        // restore user's group
        if (group is ExtendedTitleActionsGroup) {
          toolwindow.setAdditionalGearActions(group.originActions)
        }
      }
    }
  }

  private fun initSingleContentView(content: Content, supplier: SingleContentSupplier) {
    tabAdapter = TabAdapter(content, supplier.getTabs(), tabPainter, ui).also {
      Disposer.register(content, it)
      ui.tabComponent.add(it)
    }
    assert(toolbars.isEmpty())
    supplier.getToolbarActions()?.let { mainActionGroup ->
      toolbars[ToolbarType.MAIN] = createToolbar(
        supplier.getMainToolbarPlace(),
        mainActionGroup,
        content.component
      )
    }

    let {
      val contentActions = DefaultActionGroup()
      contentActions.add(CloseCurrentContentAction())
      contentActions.add(Separator.create())
      contentActions.addAll(supplier.getContentActions())
      contentActions.add(MyInvisibleAction())

      toolbars[ToolbarType.CLOSE_GROUP] = createToolbar(
        supplier.getContentToolbarPlace(),
        contentActions,
        content.component
      ).apply {
        setReservePlaceAutoPopupIcon(false)
        layoutPolicy = ActionToolbar.NOWRAP_LAYOUT_POLICY
      }
    }

    toolbars.forEach { (_, toolbar) -> ui.tabComponent.add(toolbar.component) }

    wrapper = NonOpaquePanel(HorizontalLayout(0)).also {
      MyRedispatchMouseEventListener { e ->
        // extra actions are registered in ToolWindowContentUi#initMouseListeners
        if (SwingUtilities.isLeftMouseButton(e)) {
          ui.tabComponent.parent?.let { westPanel ->
            westPanel.dispatchEvent(SwingUtilities.convertMouseEvent(e.component, e, westPanel))
          }
        }
      }.installOn(it)
      MouseDragHelper.setComponentDraggable(it, true)
      ToolWindowContentUi.initMouseListeners(it, ui, true)
      ui.tabComponent.add(it)
    }

    isSingleContentView = true
    supplier.init(toolbars[ToolbarType.MAIN], toolbars[ToolbarType.CLOSE_GROUP])
    supplier.customize(wrapper)
  }

  private fun updateSingleContentView(content: Content, supplier: SingleContentSupplier) {
    if (tabAdapter?.jbTabs != supplier.getTabs()) {
      // in case of 'reusing content' just revoke old view and create new one
      resetSingleContentView()
      initSingleContentView(content, supplier)
    }
    else {
      toolbars.forEach { (_, toolbar) ->
        toolbar.updateActionsImmediately()
      }
      supplier.customize(wrapper)
      ui.tabComponent.repaint()
    }
  }

  private fun resetSingleContentView() {
    val adapter = tabAdapter ?: error("Adapter must not be null")
    tabAdapter = null
    ui.tabComponent.remove(adapter)
    Disposer.dispose(adapter)

    toolbars.values.forEach {
      ui.tabComponent.remove(it.component)
    }
    toolbars.clear()

    ui.tabComponent.remove(wrapper ?: error("Wrapper must not be null"))
    wrapper = null

    isSingleContentView = false
    adapter.content.getSupplier()?.reset()
  }

  private fun createToolbar(place: String, group: ActionGroup, target: JComponent? = null): ActionToolbar {
    val toolbar = ActionManager.getInstance().createActionToolbar(place, group, true)
    toolbar.setTargetComponent(target)
    toolbar.component.isOpaque = false
    return toolbar
  }

  override fun isToDrawTabs(): TabsDrawMode {
    return if (isSingleContentView) TabsDrawMode.HIDE else super.isToDrawTabs()
  }

  override fun layout() {
    super.layout()

    if (isSingleContentView) {
      val component = ui.tabComponent
      component.bounds = component.bounds.apply { width = component.parent.width }

      val labelWidth = idLabel.x + idLabel.preferredSize.width
      var tabsWidth = tabAdapter?.preferredSize?.width ?: 0
      var mainToolbarWidth = toolbars[ToolbarType.MAIN]?.component?.preferredSize?.width ?: 0
      val contentToolbarWidth = toolbars[ToolbarType.CLOSE_GROUP]?.component?.preferredSize?.width ?: 0

      val minTabWidth = tabAdapter?.minimumSize?.width ?: 0
      val fixedWidth = labelWidth + mainToolbarWidth + contentToolbarWidth
      val freeWidth = component.bounds.width - fixedWidth

      if (freeWidth < minTabWidth) {
        mainToolbarWidth += freeWidth - minTabWidth
      }

      tabsWidth = maxOf(minTabWidth, minOf(freeWidth, tabsWidth))
      val wrapperWidth = maxOf(0, freeWidth - tabsWidth)

      var x = labelWidth

      tabAdapter?.apply {
        bounds = Rectangle(x, 0, tabsWidth, component.height)
        x += tabsWidth
      }

      toolbars[ToolbarType.MAIN]?.component?.apply {
        val height = preferredSize.height
        bounds = Rectangle(x, (component.height - height) / 2, mainToolbarWidth, height)
        x += mainToolbarWidth
      }

      wrapper?.apply {
        bounds = Rectangle(x, 0, wrapperWidth, component.height)
        x += wrapperWidth
      }

      toolbars[ToolbarType.CLOSE_GROUP]?.component?.apply {
        val height = preferredSize.height
        bounds = Rectangle(x, (component.height - height) / 2, contentToolbarWidth, height)
        x += contentToolbarWidth
      }
    }
  }

  override fun updateIdLabel(label: BaseLabel) {
    super.updateIdLabel(label)
    if (!isSingleContentView) {
      label.icon = null
      label.toolTipText = null
    }
    else if (myTabs.size == 1) {
      label.icon = myTabs[0].content.icon
      val displayName = myTabs[0].content.displayName
      label.text = createProcessName(
        prefix = ui.window.stripeTitle,
        title = displayName
      )
      label.toolTipText = displayName
    }
  }

  @NlsSafe
  private fun createProcessName(title: String, prefix: String? = null) = prefix?.let {
    if (ExperimentalUI.isNewToolWindowsStripes()) it else "$it:"
  } ?: title

  private inner class TabAdapter(
    val content: Content,
    val jbTabs: JBTabs,
    val tabPainter: JBTabPainter,
    val twcui: ToolWindowContentUi
  ) : NonOpaquePanel(),
      TabsListener,
      PropertyChangeListener,
      DataProvider,
      MorePopupAware,
      Disposable
  {

    private val labels = mutableListOf<MyContentTabLabel>()
    private val popupToolbar: JComponent
    private val popupHandler = object : PopupHandler() {
      override fun invokePopup(comp: Component, x: Int, y: Int) = showPopup(comp, x, y)
    }
    private val closeHandler = object : MouseAdapter() {
      override fun mouseReleased(e: MouseEvent) {
        if (UIUtil.isCloseClick(e, MouseEvent.MOUSE_RELEASED)) {
          val tabLabel = e.component as? MyContentTabLabel
          if (tabLabel != null && tabLabel.content.isCloseable) {
            tabLabel?.closeContent()
          }
        }
      }
    }

    private val containerListener = object : ContainerListener {
      override fun componentAdded(e: ContainerEvent) = update(e)
      override fun componentRemoved(e: ContainerEvent) = update(e)
      private fun update(e: ContainerEvent) {
        if (e.child is TabLabel) {
          checkAndUpdate()
        }
      }
    }

    init {
      updateTabs()

      jbTabs.addListener(object : TabsListener {
        override fun selectionChanged(oldSelection: TabInfo?, newSelection: TabInfo?) = checkAndUpdate()
        override fun tabRemoved(tabToRemove: TabInfo) = checkAndUpdate()
        override fun tabsMoved() = checkAndUpdate()
      }, this)

      jbTabs.component.addContainerListener(containerListener)

      val tabList = ActionManager.getInstance().getAction("TabList")
      val tabListGroup = DefaultActionGroup(tabList, Separator.create(), MyInvisibleAction())
      popupToolbar = createToolbar(ActionPlaces.TOOLWINDOW_POPUP, tabListGroup, this).apply {
        setReservePlaceAutoPopupIcon(false)
        layoutPolicy = ActionToolbar.NOWRAP_LAYOUT_POLICY
      }.component

      layout = HorizontalTabLayoutWithHiddenControl {
        labels.find { it.content.info == jbTabs.selectedInfo }
      }

      add(popupToolbar, HorizontalTabLayoutWithHiddenControl.CONTROL)
    }

    private fun retrieveInfo(label: MyContentTabLabel): TabInfo {
      return label.content.info
    }

    private fun checkAndUpdate() {
      val currentContent = getSingleContentOrNull()
      val currentSupplier = currentContent?.getSupplier()
      val src = currentSupplier?.getTabs()?.tabs ?: return
      val dst = labels.map(::retrieveInfo)
      if (!ContainerUtil.equalsIdentity(src, dst)) {
        updateTabs()
      }
      updateSingleContentView(currentContent, currentSupplier)
      updateLabels(labels)
      revalidate()
    }

    fun updateTabs() {
      labels.removeAll {
        retrieveInfo(it).changeSupport.removePropertyChangeListener(this)
        remove(it)
        true
      }

      val supplier = getSingleContentOrNull()?.getSupplier() ?: return
      if (jbTabs.tabs.size > 1) {
        labels.addAll(jbTabs.tabs.map { info ->
          info.changeSupport.addPropertyChangeListener(this)
          MyContentTabLabel(FakeContent(supplier, info), this@SingleContentLayout).apply {
            addMouseListener(closeHandler)
          }
        })
        labels.forEach(::add)
      }
      updateLabels(labels)
    }

    fun updateLabels(labels: List<MyContentTabLabel>) {
      labels.associateBy { it.content.info }.forEach(::copyValues)
    }

    override fun dispose() {
      labels.forEach {
        retrieveInfo(it).changeSupport.removePropertyChangeListener(this)
      }
      jbTabs.component.removeContainerListener(containerListener)
    }

    override fun getMinimumSize(): Dimension {
      if (isMinimumSizeSet) {
        return super.getMinimumSize()
      }
      val minWidth = if (labels.isEmpty()) 0 else popupToolbar.preferredSize.width
      return Dimension(minWidth, 0)
    }

    fun copyValues(from: TabInfo, to: ContentLabel) {
      to.icon = from.icon
      to.text = from.text
    }

    fun showPopup(component: Component, x: Int, y: Int) {
      // ViewContext.CONTENT_KEY
      val info = ((component as? ContentTabLabel)?.content as? FakeContent)?.info
      if (info == null) {
        logger<SingleContentLayout>().warn("Cannot resolve label popup component. Event will be ignored")
        return
      }
      val supplier = getSingleContentOrNull()?.getSupplier()
      val toShow = getPopupMenu(supplier?.getTabs()) ?: return
      toShow.setTargetComponent(component)
      JBPopupMenu.showAt(RelativePoint(component, Point(x, y)), toShow.component)
    }

    private fun getPopupMenu(tabs: JBTabs?): ActionPopupMenu? {
      val jbTabsImpl = tabs as? JBTabsImpl ?: return null
      val popup = jbTabsImpl.popupGroup ?: return null
      val popupPlace = jbTabsImpl.popupPlace ?: ActionPlaces.UNKNOWN

      val group = DefaultActionGroup()
      group.addAll(popup)
      group.addSeparator()
      group.addAll(jbTabsImpl.navigationActions)

      return ActionManager.getInstance().createActionPopupMenu(popupPlace, group)
    }

    override fun paintComponent(g: Graphics) {
      if (g is Graphics2D) {
        labels.forEach { comp ->
          val labelBounds = comp.bounds
          if (jbTabs.selectedInfo == retrieveInfo(comp)) {
            tabPainter.paintSelectedTab(JBTabsPosition.top, g, labelBounds, 1, null, twcui.window.isActive, comp.isHovered)
          }
          else {
            tabPainter.paintTab(JBTabsPosition.top, g, labelBounds, 1, null, twcui.window.isActive, comp.isHovered)
          }
        }
      }

      super.paintComponent(g)
    }

    override fun propertyChange(evt: PropertyChangeEvent) {
      val source = evt.source as? TabInfo ?: error("Bad event source")
      val label = labels.find { it.content.info == source } ?: error("Cannot find label for $source")
      copyValues(source, label)
    }

    private inner class MyContentTabLabel(content: FakeContent, layout: TabContentLayout) : ContentTabLabel(content, layout), DataProvider {

      init {
        addMouseListener(popupHandler)
      }

      override fun selectContent() {
        retrieveInfo(this).let { jbTabs.select(it, true) }
      }

      public override fun closeContent() {
        retrieveInfo(this).let { info ->
          getSingleContentOrNull()?.getSupplier()?.close(info)
        }
      }

      override fun getData(dataId: String): Any? {
        if (JBTabsEx.NAVIGATION_ACTIONS_KEY.`is`(dataId)) {
          return jbTabs
        }
        return DataManagerImpl.getDataProviderEx(retrieveInfo(this).component)?.getData(dataId)
      }

      override fun getContent(): FakeContent {
        return super.getContent() as FakeContent
      }
    }

    override fun getData(dataId: String): Any? {
      if (MorePopupAware.KEY.`is`(dataId)) {
        return this
      }
      return null
    }

    override fun canShowMorePopup(): Boolean {
      return true
    }

    override fun showMorePopup() {
      val contentToShow = labels
        .filter { it.bounds.width <= 0 }
        .map(MyContentTabLabel::getContent)

      val step = object : BaseListPopupStep<FakeContent>(null, contentToShow) {
        override fun onChosen(selectedValue: FakeContent, finalChoice: Boolean): PopupStep<*>? {
          jbTabs.select(selectedValue.info, true)
          return FINAL_CHOICE
        }

        override fun getIconFor(value: FakeContent) = value.icon

        override fun getTextFor(value: FakeContent) = value.displayName
      }

      JBPopupFactory.getInstance()
        .createListPopup(step)
        .show(RelativePoint.getSouthWestOf(popupToolbar))
    }
  }

  private class ExtendedTitleActionsGroup(
    val originActions: ActionGroup?,
    vararg extendedActions: AnAction
    ) : DefaultActionGroup() {
      init {
        extendedActions.forEach(::add)
        originActions?.let(::addAll)
      }
  }

  private inner class CloseCurrentContentAction : DumbAwareAction(CommonBundle.messagePointer("action.close"), AllIcons.Actions.Cancel) {
    override fun actionPerformed(e: AnActionEvent) {
      val content = getSingleContentOrNull()
      if (content != null && content.isPinned) {
        content.isPinned = false
      }
      else {
        content?.let { it.manager?.removeContent(it, true) }
      }
    }

    override fun update(e: AnActionEvent) {
      e.presentation.isEnabledAndVisible = getSingleContentOrNull()?.isCloseable == true
      if (isPinned()) {
        e.presentation.icon = AllIcons.General.Pin_tab
        e.presentation.text = IdeBundle.message("action.unpin.tab")
      }
      else {
        e.presentation.icon = AllIcons.Actions.Cancel
        e.presentation.text = CommonBundle.message("action.close")
      }
    }

    private fun isPinned(): Boolean {
      return getSingleContentOrNull()?.isPinned == true
    }
  }

  /**
   * The minimal [Content] implementation.
   *
   * Is used only to pass as an argument to support [SingleContentLayout.TabAdapter.MyContentTabLabel].
   *
   * All unused methods throw [IllegalStateException].
   */
  private class FakeContent(val supplier: SingleContentSupplier, val info: TabInfo) : Content {

    private val pcs = PropertyChangeSupport(this)

    override fun addPropertyChangeListener(l: PropertyChangeListener?) {
      pcs.addPropertyChangeListener(l)
    }

    override fun removePropertyChangeListener(l: PropertyChangeListener?) {
      pcs.removePropertyChangeListener(l)
    }

    override fun isSelected(): Boolean {
      return supplier.getTabs().selectedInfo == info
    }

    override fun isCloseable(): Boolean {
      return supplier.isClosable(info)
    }

    override fun isPinned(): Boolean {
      return false
    }

    override fun getManager(): ContentManager? {
      return null
    }

    override fun getIcon(): Icon? {
      return info.icon
    }

    @NlsSafe
    override fun getDisplayName(): String {
      return info.text
    }

    override fun <T : Any?> getUserData(key: Key<T>): T? {
      error("An operation is not supported")
    }

    override fun <T : Any?> putUserData(key: Key<T>, value: T?) {
      error("An operation is not supported")
    }

    override fun dispose() {
      error("An operation is not supported")
    }

    override fun getComponent(): JComponent {
      error("An operation is not supported")
    }

    override fun getPreferredFocusableComponent(): JComponent? {
      error("An operation is not supported")
    }

    override fun setComponent(component: JComponent?) {
      error("An operation is not supported")
    }

    override fun setPreferredFocusableComponent(component: JComponent?) {
      error("An operation is not supported")
    }

    override fun setPreferredFocusedComponent(computable: Computable<out JComponent>?) {
      error("An operation is not supported")
    }

    override fun setIcon(icon: Icon?) {
      error("An operation is not supported")
    }

    override fun setDisplayName(displayName: String?) {
      error("An operation is not supported")
    }

    override fun setTabName(tabName: String?) {
      error("An operation is not supported")
    }

    override fun getTabName(): String {
      error("An operation is not supported")
    }

    override fun getToolwindowTitle(): String {
      error("An operation is not supported")
    }

    override fun setToolwindowTitle(toolwindowTitle: String?) {
      error("An operation is not supported")
    }

    override fun getDisposer(): Disposable? {
      error("An operation is not supported")
    }

    override fun setDisposer(disposer: Disposable) {
      error("An operation is not supported")
    }

    override fun setShouldDisposeContent(value: Boolean) {
      error("An operation is not supported")
    }

    override fun getDescription(): String? {
      error("An operation is not supported")
    }

    override fun setDescription(description: String?) {
      error("An operation is not supported")
    }

    override fun release() {
      error("An operation is not supported")
    }

    override fun isValid(): Boolean {
      error("An operation is not supported")
    }

    override fun setPinned(locked: Boolean) {
      error("An operation is not supported")
    }

    override fun setPinnable(pinnable: Boolean) {
      error("An operation is not supported")
    }

    override fun isPinnable(): Boolean {
      error("An operation is not supported")
    }

    override fun setCloseable(closeable: Boolean) {
      error("An operation is not supported")
    }

    override fun setActions(actions: ActionGroup?, place: String?, contextComponent: JComponent?) {
      error("An operation is not supported")
    }

    override fun getActions(): ActionGroup? {
      error("An operation is not supported")
    }

    override fun setSearchComponent(comp: JComponent?) {
      error("An operation is not supported")
    }

    override fun getSearchComponent(): JComponent? {
      error("An operation is not supported")
    }

    override fun getPlace(): String {
      error("An operation is not supported")
    }

    override fun getActionsContextComponent(): JComponent? {
      error("An operation is not supported")
    }

    override fun setAlertIcon(icon: AlertIcon?) {
      error("An operation is not supported")
    }

    override fun getAlertIcon(): AlertIcon? {
      error("An operation is not supported")
    }

    override fun fireAlert() {
      error("An operation is not supported")
    }

    override fun getBusyObject(): BusyObject? {
      error("An operation is not supported")
    }

    override fun setBusyObject(`object`: BusyObject?) {
      error("An operation is not supported")
    }

    override fun getSeparator(): String {
      error("An operation is not supported")
    }

    override fun setSeparator(separator: String?) {
      error("An operation is not supported")
    }

    override fun setPopupIcon(icon: Icon?) {
      error("An operation is not supported")
    }

    override fun getPopupIcon(): Icon? {
      error("An operation is not supported")
    }

    override fun setExecutionId(executionId: Long) {
      error("An operation is not supported")
    }

    override fun getExecutionId(): Long {
      error("An operation is not supported")
    }
  }

  private class HorizontalTabLayoutWithHiddenControl(
    val selected: () -> JComponent?
  ) : AbstractLayoutManager() {

    private var control: Component? = null

    override fun addLayoutComponent(comp: Component?, constraints: Any?) {
      if (constraints == CONTROL) {
        assert(control == null) { "Cannot add a second control component" }
        control = comp
      }
      super.addLayoutComponent(comp, constraints)
    }

    override fun removeLayoutComponent(comp: Component?) {
      if (control === comp) {
        control = null
      }
      super.removeLayoutComponent(comp)
    }

    override fun preferredLayoutSize(parent: Container): Dimension {
      return parent.components.asSequence()
        .filterNot { it === control }
        .map { it.preferredSize }
        .fold(Dimension()) { acc, size ->
          acc.apply {
            width += size.width
            height = maxOf(acc.height, size.height, parent.height)
          }
        }
    }

    override fun layoutContainer(parent: Container) {
      var eachX = 0
      val canFitAllComponents = parent.preferredSize.width <= parent.bounds.width
      val children = parent.components.asSequence().filterNot { it === control }
      if (canFitAllComponents) {
          children.forEach {
            val dim = it.preferredSize
            it.bounds = Rectangle(eachX, 0, dim.width, parent.height)
            eachX += dim.width
          }
        control?.bounds = Rectangle(0, 0, 0, 0)
      }
      else {
        // copy of [TabContentLayout#layout]
        val toLayout = children.toMutableList()
        val toRemove = mutableListOf<Component>()
        var requiredWidth = toLayout.sumOf { it.preferredSize.width }
        val selected = selected()
        val toFitWidth = parent.bounds.width - (control?.preferredSize?.width ?: 0)
        while (true) {
          if (requiredWidth <= toFitWidth) break
          if (toLayout.size <= 1) break

          if (toLayout.first() != selected) {
            requiredWidth -= toLayout.first().preferredSize.width + 1
            toRemove += toLayout.first()
            toLayout.removeFirst()
          }
          else if (toLayout.last() != selected) {
            requiredWidth -= toLayout.last().preferredSize.width + 1
            toRemove += toLayout.last()
            toLayout.removeLast()
          }
          else {
            break
          }
        }
        toLayout.forEach {
          val prefSize = it.preferredSize
          it.bounds = Rectangle(eachX, 0, minOf(prefSize.width, toFitWidth), parent.height)
          eachX += prefSize.width
        }
        toRemove.forEach {
          it.bounds = Rectangle(0, 0, 0, 0)
        }
        control?.let {
          val controlPrefSize = it.preferredSize
          it.bounds = Rectangle(Point(toFitWidth, (parent.height - controlPrefSize.height) / 2), controlPrefSize)
        }
      }
    }

    companion object {
      const val CONTROL = "ControlComponent"
    }
  }

  /**
   * Workaround action to prevent [Separator] disappearing when [SingleContentSupplier.getContentActions] is empty.
   */
  private class MyInvisibleAction : DumbAwareAction(), CustomComponentAction {

    override fun actionPerformed(e: AnActionEvent) {
      error("An operation is not supported")
    }

    override fun createCustomComponent(presentation: Presentation, place: String): JComponent {
      return NonOpaquePanel()
    }
  }

  private class MyRedispatchMouseEventListener(
    val redispatch: (MouseEvent) -> Unit
  ) : MouseListener, MouseMotionListener {

    fun installOn(component: Component) {
      component.addMouseListener(this)
      component.addMouseMotionListener(this)
    }

    override fun mouseClicked(e: MouseEvent) = redispatch(e)

    override fun mousePressed(e: MouseEvent) = redispatch(e)

    override fun mouseReleased(e: MouseEvent) = redispatch(e)

    override fun mouseEntered(e: MouseEvent) = redispatch(e)

    override fun mouseExited(e: MouseEvent) = redispatch(e)

    override fun mouseDragged(e: MouseEvent) = redispatch(e)

    override fun mouseMoved(e: MouseEvent) = redispatch(e)
  }

  private enum class ToolbarType {
    MAIN, CLOSE_GROUP
  }
}