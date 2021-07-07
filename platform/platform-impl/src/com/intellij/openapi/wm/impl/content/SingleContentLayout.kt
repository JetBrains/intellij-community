// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.content

import com.intellij.CommonBundle
import com.intellij.icons.AllIcons
import com.intellij.ide.impl.DataManagerImpl
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.JBPopupMenu
import com.intellij.openapi.util.*
import com.intellij.ui.PopupHandler
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.components.panels.HorizontalLayout
import com.intellij.ui.components.panels.NonOpaquePanel
import com.intellij.ui.content.AlertIcon
import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentManager
import com.intellij.ui.tabs.*
import com.intellij.ui.tabs.impl.JBTabsImpl
import com.intellij.ui.tabs.impl.TabLabel
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.*
import java.awt.event.ContainerEvent
import java.awt.event.ContainerListener
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.beans.PropertyChangeEvent
import java.beans.PropertyChangeListener
import java.beans.PropertyChangeSupport
import java.util.function.Supplier
import javax.swing.Icon
import javax.swing.JComponent

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
  private val toolbars = mutableMapOf<ActionToolbarPosition, ActionToolbar>()
  private val wrapper = NonOpaquePanel(HorizontalLayout(0))

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
    return if (myTabs.size == 1) myTabs[0].content else null
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
  }

  private fun initSingleContentView(content: Content, supplier: SingleContentSupplier) {
    tabAdapter = TabAdapter(content, supplier.getTabs(), tabPainter, myUi).also {
      Disposer.register(content, it)
      myUi.tabComponent.add(it)
    }
    assert(toolbars.isEmpty())
    supplier.getToolbarActions()?.let { mainActionGroup ->
      toolbars[ActionToolbarPosition.LEFT] = createToolbar(
        "MainSingleContentToolbar",
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

      toolbars[ActionToolbarPosition.RIGHT] = createToolbar(
        "CloseSingleContentGroup",
        contentActions,
        content.component
      ).apply {
        setReservePlaceAutoPopupIcon(false)
        layoutPolicy = ActionToolbar.NOWRAP_LAYOUT_POLICY
      }
    }

    toolbars.forEach { (_, toolbar) -> myUi.tabComponent.add(toolbar.component) }

    wrapper.removeAll()
    ToolWindowContentUi.initMouseListeners(wrapper, myUi, true)
    myUi.tabComponent.add(wrapper)

    isSingleContentView = true
    supplier.init(toolbars[ActionToolbarPosition.LEFT], toolbars[ActionToolbarPosition.RIGHT])
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
      myUi.tabComponent.repaint()
    }
  }

  private fun resetSingleContentView() {
    val adapter = tabAdapter ?: error("Adapter must not be null")
    tabAdapter = null
    myUi.tabComponent.remove(adapter)
    Disposer.dispose(adapter)

    toolbars.values.forEach {
      myUi.tabComponent.remove(it.component)
    }
    toolbars.clear()

    myUi.tabComponent.remove(wrapper)
    wrapper.removeAll()

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
      val component = myUi.tabComponent
      component.bounds = component.bounds.apply { width = component.parent.width }

      var toolbarWidth = myIdLabel.x + myIdLabel.width

      tabAdapter?.apply {
        bounds = Rectangle(toolbarWidth, 0, preferredSize.width, component.height)
        toolbarWidth += bounds.width
      }

      val rightToolbarWidth = toolbars[ActionToolbarPosition.RIGHT]?.component?.run {
        bounds = Rectangle(component.width - preferredSize.width, 0, preferredSize.width, component.height)
        bounds.width
      } ?: 0

      toolbars[ActionToolbarPosition.LEFT]?.component?.apply {
        bounds = Rectangle(
          toolbarWidth,
          0,
          minOf(preferredSize.width, component.width - rightToolbarWidth - toolbarWidth),
          component.height
        )
        toolbarWidth += bounds.width
      }

      wrapper.bounds = Rectangle(toolbarWidth, 0, maxOf(component.width - rightToolbarWidth - toolbarWidth, 0), component.height)
    }
  }

  override fun updateIdLabel(label: BaseLabel) {
    if (!isSingleContentView) {
      label.icon = null
      super.updateIdLabel(label)
    }
    else if (myTabs.size == 1) {
      label.icon = myTabs[0].content.icon
      label.text = createProcessName(
        prefix = myUi.window.stripeTitle,
        title = myTabs[0].content.displayName
      )
      label.border = JBUI.Borders.empty(0, 2, 0, 10)
    }
  }

  @NlsSafe
  private fun createProcessName(title: String, prefix: String? = null) = prefix?.let { "$it $title" } ?: title

  private inner class TabAdapter(
    val content: Content,
    val jbTabs: JBTabs,
    val tabPainter: JBTabPainter,
    val twcui: ToolWindowContentUi
  ) : NonOpaquePanel(HorizontalLayout(0)), TabsListener, PropertyChangeListener, Disposable {

    val labels = mutableListOf<MyContentTabLabel>()

    val popupHandler = object : PopupHandler() {
      override fun invokePopup(comp: Component, x: Int, y: Int) = showPopup(comp, x, y)
    }

    val closeHandler = object : MouseAdapter() {
      override fun mouseReleased(e: MouseEvent) {
        if (UIUtil.isCloseClick(e, MouseEvent.MOUSE_RELEASED)) {
          val tabLabel = e.component as? MyContentTabLabel
          if (tabLabel != null && tabLabel.content.isCloseable) {
            retrieveInfo(tabLabel).let(jbTabs::removeTab)
          }
        }
      }
    }

    val containerListener = object : ContainerListener {
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

      override fun closeContent() {
        retrieveInfo(this).let(jbTabs::removeTab)
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
  }

  private inner class CloseCurrentContentAction : DumbAwareAction(CommonBundle.messagePointer("action.close"), AllIcons.Actions.Cancel) {
    override fun actionPerformed(e: AnActionEvent) {
      getSingleContentOrNull()?.let { it.manager?.removeContent(it, true) }
    }

    override fun update(e: AnActionEvent) {
      e.presentation.isEnabledAndVisible = getSingleContentOrNull()?.isCloseable == true
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
      error("An operation is not supported")
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

    override fun getIcon(): Icon? {
      error("An operation is not supported")
    }

    override fun setDisplayName(displayName: String?) {
      error("An operation is not supported")
    }

    override fun getDisplayName(): String? {
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

  /**
   * Workaround action to prevent [Separator] disappearing when [SingleContentSupplier.getContentActions] is empty.
   */
  private class MyInvisibleAction : DumbAwareAction(), CustomComponentAction {

    override fun actionPerformed(e: AnActionEvent) {
      error("An operation is not supported")
    }

    override fun createCustomComponent(presentation: Presentation): JComponent {
      return NonOpaquePanel()
    }
  }
}