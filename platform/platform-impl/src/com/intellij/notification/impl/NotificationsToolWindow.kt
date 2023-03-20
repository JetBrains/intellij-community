// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.notification.impl

import com.intellij.UtilBundle
import com.intellij.icons.AllIcons
import com.intellij.ide.DataManager
import com.intellij.ide.IdeBundle
import com.intellij.ide.ui.LafManagerListener
import com.intellij.ide.ui.UISettingsListener
import com.intellij.idea.ActionsBundle
import com.intellij.notification.ActionCenter
import com.intellij.notification.Notification
import com.intellij.notification.impl.ui.NotificationsUtil
import com.intellij.notification.impl.widget.IdeNotificationArea
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.TooltipDescriptionProvider
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Divider
import com.intellij.openapi.ui.NullableComponent
import com.intellij.openapi.ui.OnePixelDivider
import com.intellij.openapi.ui.Splitter
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupListener
import com.intellij.openapi.ui.popup.LightweightWindowEvent
import com.intellij.openapi.util.Clock
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.wm.*
import com.intellij.openapi.wm.ex.ToolWindowEx
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import com.intellij.ui.*
import com.intellij.ui.components.*
import com.intellij.ui.components.labels.LinkLabel
import com.intellij.ui.components.labels.LinkListener
import com.intellij.ui.components.panels.HorizontalLayout
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.Alarm
import com.intellij.util.text.DateFormatUtil
import com.intellij.util.ui.*
import org.jetbrains.annotations.Nls
import java.awt.*
import java.awt.event.*
import java.beans.PropertyChangeEvent
import java.beans.PropertyChangeListener
import java.util.*
import java.util.function.Consumer
import javax.accessibility.AccessibleContext
import javax.swing.*
import javax.swing.event.DocumentEvent
import javax.swing.event.HyperlinkEvent
import javax.swing.event.PopupMenuEvent
import javax.swing.text.JTextComponent

internal class NotificationsToolWindowFactory : ToolWindowFactory, DumbAware {
  companion object {
    const val ID = "Notifications"
    internal const val CLEAR_ACTION_ID = "ClearAllNotifications"

    internal val myModel = ApplicationNotificationModel()

    fun addNotification(project: Project?, notification: Notification) {
      if (notification.canShowFor(project) && NotificationsConfigurationImpl.getSettings(notification.groupId).isShouldLog) {
        myModel.addNotification(project, notification)
      }
    }

    fun expire(notification: Notification) {
      myModel.expire(notification)
    }

    fun expireAll() {
      myModel.expireAll()
    }

    fun clearAll(project: Project?) {
      myModel.clearAll(project)
    }

    fun getStateNotifications(project: Project): List<Notification> = myModel.getStateNotifications(project)

    fun getNotifications(project: Project?): List<Notification> = myModel.getNotifications(project)

    fun getStatusMessage(project: Project?): StatusMessage? = project?.let { myModel.getStatusMessage(it) }

    fun setStatusMessage(project: Project, notification: Notification?) {
      myModel.setStatusMessage(project, notification)
    }
  }

  override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
    toolWindow.setContentUiType(ToolWindowContentUiType.TABBED, null)
    NotificationContent(project, toolWindow)
  }
}

@JvmRecord
data class StatusMessage(val notification: Notification, val text: @NlsContexts.StatusBarText String, val stamp: Long)

internal class NotificationContent(val project: Project,
                                   private val toolWindow: ToolWindow) : Disposable, ToolWindowManagerListener {
  private val myMainPanel = JBPanelWithEmptyText(BorderLayout())

  private val myNotifications = ArrayList<Notification>()
  private val myIconNotifications = ArrayList<Notification>()

  private val suggestions: NotificationGroupComponent
  private val timeline: NotificationGroupComponent
  private val searchController: SearchController
  private val autoProportionController: AutoProportionController

  private val singleSelectionHandler = SingleTextSelectionHandler()

  private var myVisible = true

  private val mySearchUpdateAlarm = Alarm()

  init {
    myMainPanel.background = NotificationComponent.BG_COLOR
    setEmptyState()
    handleFocus()

    suggestions = NotificationGroupComponent(this, true, project)
    timeline = NotificationGroupComponent(this, false, project)
    searchController = SearchController(this, suggestions, timeline)

    myMainPanel.add(createSearchComponent(), BorderLayout.NORTH)

    createGearActions()

    val splitter = MySplitter()
    splitter.firstComponent = suggestions
    splitter.secondComponent = timeline
    myMainPanel.add(splitter)

    autoProportionController = AutoProportionController(splitter, suggestions, timeline)

    suggestions.setRemoveCallback(Consumer(::remove))
    timeline.setRemoveCallback(Consumer(::remove))
    timeline.setClearCallback(::clear)

    Disposer.register(toolWindow.disposable, this)

    val content = ContentFactory.getInstance().createContent(myMainPanel, "", false)
    content.preferredFocusableComponent = myMainPanel

    val contentManager = toolWindow.contentManager
    contentManager.addContent(content)
    contentManager.setSelectedContent(content)

    project.messageBus.connect(toolWindow.disposable).subscribe(ToolWindowManagerListener.TOPIC, this)

    val connection = ApplicationManager.getApplication().messageBus.connect(toolWindow.disposable)
    connection.subscribe(LafManagerListener.TOPIC, LafManagerListener {
      suggestions.updateLaf()
      timeline.updateLaf()
    })
    connection.subscribe(UISettingsListener.TOPIC, UISettingsListener {
      suggestions.updateLaf()
      timeline.updateLaf()
    })

    val newNotifications = ArrayList<Notification>()
    NotificationsToolWindowFactory.myModel.registerAndGetInitNotifications(this, newNotifications)
    for (notification in newNotifications) {
      add(notification)
    }
  }

  private fun createSearchComponent(): SearchTextField {
    val searchField = object : SearchTextField(false) {
      override fun updateUI() {
        super.updateUI()
        textEditor?.border = null
      }

      override fun preprocessEventForTextField(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.VK_ESCAPE && event.id == KeyEvent.KEY_PRESSED) {
          isVisible = false
          searchController.cancelSearch()
          return true
        }
        return super.preprocessEventForTextField(event)
      }
    }
    searchField.textEditor.border = null
    searchField.border = JBUI.Borders.customLineBottom(JBColor.border())
    searchField.isVisible = false

    if (ExperimentalUI.isNewUI()) {
      searchController.background = JBUI.CurrentTheme.ToolWindow.background()
      searchField.textEditor.background = searchController.background
    }
    else {
      searchController.background = UIUtil.getTextFieldBackground()
    }

    searchController.searchField = searchField

    searchField.addDocumentListener(object : DocumentAdapter() {
      override fun textChanged(e: DocumentEvent) {
        mySearchUpdateAlarm.cancelAllRequests()
        mySearchUpdateAlarm.addRequest(searchController::doSearch, 100, ModalityState.stateForComponent(searchField))
      }
    })

    return searchField
  }

  private fun createGearActions() {
    val gearAction = object : DumbAwareAction() {
      override fun actionPerformed(e: AnActionEvent) {
        searchController.startSearch()
      }
    }

    val actionManager = ActionManager.getInstance()
    val findAction = actionManager.getAction(IdeActions.ACTION_FIND)
    if (findAction == null) {
      gearAction.templatePresentation.text = ActionsBundle.actionText(IdeActions.ACTION_FIND)
    }
    else {
      gearAction.copyFrom(findAction)
      gearAction.registerCustomShortcutSet(findAction.shortcutSet, myMainPanel)
    }

    val group = DefaultActionGroup()
    group.add(gearAction)
    group.addSeparator()

    val clearAction = actionManager.getAction(NotificationsToolWindowFactory.CLEAR_ACTION_ID)
    if (clearAction != null) {
      group.add(clearAction)
    }

    val markAction = actionManager.getAction("MarkNotificationsAsRead")
    if (markAction != null) {
      group.add(markAction)
    }

    (toolWindow as ToolWindowEx).setAdditionalGearActions(group)
  }

  fun setEmptyState() {
    myMainPanel.emptyText.appendLine(IdeBundle.message("notifications.toolwindow.empty.text.first.line"))
    @Suppress("DialogTitleCapitalization")
    myMainPanel.emptyText.appendLine(IdeBundle.message("notifications.toolwindow.empty.text.second.line"))
  }

  fun clearEmptyState() {
    myMainPanel.emptyText.clear()
  }

  private fun handleFocus() {
    val listener = AWTEventListener {
      if (it is MouseEvent && it.id == MouseEvent.MOUSE_PRESSED && !toolWindow.isActive && UIUtil.isAncestor(myMainPanel, it.component)) {
        it.component.requestFocus()
      }
    }
    Toolkit.getDefaultToolkit().addAWTEventListener(listener, AWTEvent.MOUSE_EVENT_MASK)
    Disposer.register(toolWindow.disposable, Disposable {
      Toolkit.getDefaultToolkit().removeAWTEventListener(listener)
    })
  }

  fun add(notification: Notification) {
    if (!NotificationsConfigurationImpl.getSettings(notification.groupId).isShouldLog) {
      return
    }
    if (notification.isSuggestionType) {
      suggestions.add(notification, singleSelectionHandler)
    }
    else {
      timeline.add(notification, singleSelectionHandler)
    }
    myNotifications.add(notification)
    myIconNotifications.add(notification)
    searchController.update()
    autoProportionController.update()
    setStatusMessage(notification)
    updateIcon()
  }

  fun getStateNotifications() = ArrayList(myIconNotifications)

  fun getNotifications() = ArrayList(myNotifications)

  fun isEmpty() = suggestions.isEmpty() && timeline.isEmpty()

  fun expire(notification: Notification?) {
    if (notification == null) {
      val notifications = ArrayList(myNotifications)

      myNotifications.clear()
      myIconNotifications.clear()
      suggestions.expireAll()
      timeline.expireAll()
      searchController.update()
      setStatusMessage(null)
      updateIcon()

      for (n in notifications) {
        n.expire()
      }
    }
    else {
      remove(notification)
    }
  }

  private fun remove(notification: Notification) {
    if (notification.isSuggestionType) {
      suggestions.remove(notification)
    }
    else {
      timeline.remove(notification)
    }
    myNotifications.remove(notification)
    myIconNotifications.remove(notification)
    searchController.update()
    autoProportionController.update()
    setStatusMessage()
    updateIcon()
  }

  private fun clear(notifications: List<Notification>) {
    myNotifications.removeAll(notifications)
    myIconNotifications.removeAll(notifications)
    searchController.update()
    autoProportionController.update()
    setStatusMessage()
    updateIcon()
  }

  fun clearAll() {
    project.closeAllBalloons()

    myNotifications.clear()
    myIconNotifications.clear()

    suggestions.clear()
    timeline.clear()

    searchController.update()

    setStatusMessage(null)
    updateIcon()
  }

  override fun stateChanged(toolWindowManager: ToolWindowManager) {
    val visible = toolWindow.isVisible
    if (myVisible != visible) {
      myVisible = visible
      if (visible) {
        project.closeAllBalloons()

        suggestions.updateComponents()
        timeline.updateComponents()
      }
      else {
        suggestions.clearNewState()
        timeline.clearNewState()
        myIconNotifications.clear()
        updateIcon()
      }
    }
  }

  private fun updateIcon() {
    toolWindow.setIcon(IdeNotificationArea.getActionCenterNotificationIcon(myIconNotifications))
    ActionCenter.fireModelChanged()
  }

  private fun setStatusMessage() {
    setStatusMessage(myNotifications.findLast { it.isImportant || it.isImportantSuggestion })
  }

  private fun setStatusMessage(notification: Notification?) {
    NotificationsToolWindowFactory.setStatusMessage(project, notification)
  }

  override fun dispose() {
    NotificationsToolWindowFactory.myModel.unregister(this)
    Disposer.dispose(mySearchUpdateAlarm)
  }

  fun fullRepaint() {
    myMainPanel.doLayout()
    myMainPanel.revalidate()
    myMainPanel.repaint()
  }
}

private class MySplitter : OnePixelSplitter(true, .5f) {
  override fun createDivider(): Divider {
    return object : OnePixelDivider(true, this) {
      override fun setVisible(aFlag: Boolean) {
        super.setVisible(aFlag)
        setResizeEnabled(aFlag)
        if (!aFlag) {
          setBounds(0, 0, 0, 0)
        }
      }

      override fun setBackground(bg: Color?) {
        super.setBackground(JBColor.border())
      }
    }
  }
}

private fun JComponent.mediumFontFunction() {
  font = JBFont.medium()
  val f: (JComponent) -> Unit = {
    it.font = JBFont.medium()
  }
  putClientProperty(NotificationGroupComponent.FONT_KEY, f)
}

private fun JComponent.smallFontFunction() {
  font = JBFont.smallOrNewUiMedium()
  val f: (JComponent) -> Unit = {
    it.font = JBFont.smallOrNewUiMedium()
  }
  putClientProperty(NotificationGroupComponent.FONT_KEY, f)
}

private class SearchController(private val mainContent: NotificationContent,
                               private val suggestions: NotificationGroupComponent,
                               private val timeline: NotificationGroupComponent) {
  lateinit var searchField: SearchTextField
  lateinit var background: Color

  fun startSearch() {
    searchField.isVisible = true
    searchField.selectText()
    searchField.requestFocus()

    mainContent.clearEmptyState()

    if (searchField.text.isNotEmpty()) {
      doSearch()
    }
  }

  fun doSearch() {
    val query = searchField.text

    if (query.isEmpty()) {
      searchField.textEditor.background = background
      clearSearch()
      return
    }

    var result = false
    val function: (NotificationComponent) -> Unit = {
      if (it.applySearchQuery(query)) {
        result = true
      }
    }
    suggestions.iterateComponents(function)
    timeline.iterateComponents(function)
    searchField.textEditor.background = if (result) background else LightColors.RED
    mainContent.fullRepaint()
  }

  fun update() {
    if (searchField.isVisible && searchField.text.isNotEmpty()) {
      doSearch()
    }
  }

  fun cancelSearch() {
    mainContent.setEmptyState()
    clearSearch()
  }

  private fun clearSearch() {
    val function: (NotificationComponent) -> Unit = { it.applySearchQuery(null) }
    suggestions.iterateComponents(function)
    timeline.iterateComponents(function)
    mainContent.fullRepaint()
  }
}

private class AutoProportionController(private val splitter: MySplitter,
                                       private val suggestions: NotificationGroupComponent,
                                       private val timeline: NotificationGroupComponent) : PropertyChangeListener {
  private var myEvent = false
  private var myEnabled = true

  init {
    splitter.addPropertyChangeListener(Splitter.PROP_PROPORTION, this)
  }

  override fun propertyChange(evt: PropertyChangeEvent?) {
    if (!myEvent) {
      myEnabled = false
      splitter.removePropertyChangeListener(Splitter.PROP_PROPORTION, this)
    }
  }

  fun update() {
    ApplicationManager.getApplication().invokeLater(::doUpdate)
  }

  private fun doUpdate() {
    if (!myEnabled || suggestions.isEmpty() || timeline.isEmpty()) {
      return
    }

    val height = splitter.height
    if (height == 0) {
      return
    }

    val firstHeight = suggestions.preferredSize.height + JBUI.scale(10)

    if (firstHeight < height / 2) {
      setProportion(firstHeight / height.toFloat())
    }
    else {
      setProportion(0.5f)
    }
  }

  private fun setProportion(value: Float) {
    try {
      myEvent = true
      splitter.proportion = value
    }
    finally {
      myEvent = false
    }
  }
}

private class NotificationGroupComponent(private val myMainContent: NotificationContent,
                                         private val mySuggestionType: Boolean,
                                         private val myProject: Project) :
  JBPanel<NotificationGroupComponent>(BorderLayout()), NullableComponent {

  companion object {
    const val FONT_KEY = "FontFunction"
  }

  private val myTitle = JBLabel(
    IdeBundle.message(if (mySuggestionType) "notifications.toolwindow.suggestions" else "notifications.toolwindow.timeline"))

  private val myList = JPanel(VerticalLayout(JBUI.scale(10)))
  private val myScrollPane = object : JBScrollPane(myList, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                                                   ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER) {
    override fun setupCorners() {
      super.setupCorners()
      border = null
    }

    override fun updateUI() {
      super.updateUI()
      border = null
    }
  }

  private var myScrollValue = 0

  private val myEventHandler = ComponentEventHandler()

  private val myTimeComponents = ArrayList<JLabel>()
  private val myTimeAlarm = Alarm(myProject)

  private lateinit var myClearCallback: (List<Notification>) -> Unit
  private lateinit var myRemoveCallback: Consumer<Notification>

  init {
    background = NotificationComponent.BG_COLOR

    val mainPanel = JPanel(BorderLayout(0, JBUI.scale(8)))
    mainPanel.isOpaque = false
    mainPanel.border = JBUI.Borders.empty(8, 8, 0, 0)
    add(mainPanel)

    myTitle.mediumFontFunction()
    myTitle.foreground = NotificationComponent.INFO_COLOR

    if (mySuggestionType) {
      myTitle.border = JBUI.Borders.emptyLeft(10)
      mainPanel.add(myTitle, BorderLayout.NORTH)
    }
    else {
      val panel = JPanel(BorderLayout())
      panel.isOpaque = false
      panel.border = JBUI.Borders.emptyLeft(10)

      panel.add(myTitle, BorderLayout.WEST)

      val clearAll = LinkLabel(IdeBundle.message("notifications.toolwindow.timeline.clear.all"), null) { _: LinkLabel<Unit>, _: Unit? ->
        clearAll()
      }
      clearAll.mediumFontFunction()
      clearAll.border = JBUI.Borders.emptyRight(20)
      panel.add(clearAll, BorderLayout.EAST)

      mainPanel.add(panel, BorderLayout.NORTH)
    }

    myList.isOpaque = true
    myList.background = NotificationComponent.BG_COLOR
    myList.border = JBUI.Borders.emptyRight(10)

    myScrollPane.border = null
    mainPanel.add(myScrollPane)

    myScrollPane.verticalScrollBar.addAdjustmentListener {
      val value = it.value
      if (myScrollValue == 0 && value > 0 || myScrollValue > 0 && value == 0) {
        myScrollValue = value
        repaint()
      }
      else {
        myScrollValue = value
      }
    }

    myEventHandler.add(this)
  }

  fun updateLaf() {
    updateComponents()
    iterateComponents { it.updateLaf() }
  }

  override fun paintComponent(g: Graphics) {
    super.paintComponent(g)
    if (myScrollValue > 0) {
      g.color = JBColor.border()
      val y = myScrollPane.y - 1
      g.drawLine(0, y, width, y)
    }
  }

  fun add(notification: Notification, singleSelectionHandler: SingleTextSelectionHandler) {
    val component = NotificationComponent(myProject, notification, myTimeComponents, singleSelectionHandler)
    component.setNew(true)

    myList.add(component, 0)
    updateLayout()
    myEventHandler.add(component)

    updateContent()

    component.setDoNotAskHandler { forProject ->
      component.myNotificationWrapper.notification!!
        .setDoNotAskFor(if (forProject) myProject else null)
        .also { myRemoveCallback.accept(it) }
        .hideBalloon()
    }

    component.setRemoveCallback(myRemoveCallback)
  }

  private fun updateLayout() {
    val layout = myList.layout
    iterateComponents { component ->
      layout.removeLayoutComponent(component)
      layout.addLayoutComponent(null, component)
    }
  }

  fun isEmpty(): Boolean {
    val count = myList.componentCount
    for (i in 0 until count) {
      if (myList.getComponent(i) is NotificationComponent) {
        return false
      }
    }
    return true
  }

  fun setRemoveCallback(callback: Consumer<Notification>) {
    myRemoveCallback = callback
  }

  fun remove(notification: Notification) {
    val count = myList.componentCount
    for (i in 0 until count) {
      val component = myList.getComponent(i) as NotificationComponent
      if (component.myNotificationWrapper.notification === notification) {
        if (notification.isSuggestionType) {
          component.removeFromParent()
          myList.remove(i)
        }
        else {
          component.expire()
        }
        break
      }
    }
    updateContent()
  }

  fun setClearCallback(callback: (List<Notification>) -> Unit) {
    myClearCallback = callback
  }

  private fun clearAll() {
    myProject.closeAllBalloons()

    val notifications = ArrayList<Notification>()
    iterateComponents {
      val notification = it.myNotificationWrapper.notification
      if (notification != null) {
        notifications.add(notification)
      }
    }
    clear()
    myClearCallback.invoke(notifications)
  }

  fun expireAll() {
    if (mySuggestionType) {
      clear()
    }
    else {
      iterateComponents {
        if (it.myNotificationWrapper.notification != null) {
          it.expire()
        }
      }
      updateContent()
    }
  }

  fun clear() {
    iterateComponents { it.removeFromParent() }
    myList.removeAll()
    updateContent()
  }

  fun clearNewState() {
    iterateComponents { it.setNew(false) }
  }

  fun updateComponents() {
    UIUtil.uiTraverser(this).filter(JComponent::class.java).forEach {
      val value = it.getClientProperty(FONT_KEY)
      if (value != null) {
        (value as (JComponent) -> Unit).invoke(it)
      }
    }
    myMainContent.fullRepaint()
  }

  inline fun iterateComponents(f: (NotificationComponent) -> Unit) {
    val count = myList.componentCount
    for (i in 0 until count) {
      f.invoke(myList.getComponent(i) as NotificationComponent)
    }
  }

  private fun updateContent() {
    if (!mySuggestionType && !myTimeAlarm.isDisposed) {
      myTimeAlarm.cancelAllRequests()

      object : Runnable {
        override fun run() {
          for (timeComponent in myTimeComponents) {
            timeComponent.text = formatPrettyDateTime(timeComponent.getClientProperty(NotificationComponent.TIME_KEY) as Long)
          }

          if (myTimeComponents.isNotEmpty() && !myTimeAlarm.isDisposed) {
            myTimeAlarm.addRequest(this, 30000)
          }
        }
      }.run()
    }

    myMainContent.fullRepaint()
  }

  private fun formatPrettyDateTime(time: Long): @NlsSafe String {
    val c = Calendar.getInstance()

    c.timeInMillis = Clock.getTime()
    val currentYear = c[Calendar.YEAR]
    val currentDayOfYear = c[Calendar.DAY_OF_YEAR]

    c.timeInMillis = time
    val year = c[Calendar.YEAR]
    val dayOfYear = c[Calendar.DAY_OF_YEAR]

    if (currentYear == year && currentDayOfYear == dayOfYear) {
      return DateFormatUtil.formatTime(time)
    }

    val isYesterdayOnPreviousYear = currentYear == year + 1 && currentDayOfYear == 1 && dayOfYear == c.getActualMaximum(
      Calendar.DAY_OF_YEAR)
    val isYesterday = isYesterdayOnPreviousYear || currentYear == year && currentDayOfYear == dayOfYear + 1
    if (isYesterday) {
      return UtilBundle.message("date.format.yesterday")
    }

    return DateFormatUtil.formatDate(time)
  }

  override fun isVisible(): Boolean {
    if (super.isVisible()) {
      val count = myList.componentCount
      for (i in 0 until count) {
        if (myList.getComponent(i).isVisible) {
          return true
        }
      }
    }
    return false
  }

  override fun isNull(): Boolean = !isVisible
}

private class NotificationComponent(val project: Project,
                                    notification: Notification,
                                    timeComponents: ArrayList<JLabel>,
                                    val singleSelectionHandler: SingleTextSelectionHandler) :
  JBPanel<NotificationComponent>() {

  companion object {
    val BG_COLOR: Color
      get() {
        if (ExperimentalUI.isNewUI()) {
          return JBUI.CurrentTheme.ToolWindow.background()
        }
        return UIUtil.getListBackground()
      }
    val INFO_COLOR = JBColor.namedColor("Label.infoForeground", JBColor(Gray.x80, Gray.x8C))
    internal const val NEW_COLOR_NAME = "NotificationsToolwindow.newNotification.background"
    internal val NEW_DEFAULT_COLOR = JBColor(0xE6EEF7, 0x45494A)
    val NEW_COLOR = JBColor.namedColor(NEW_COLOR_NAME, NEW_DEFAULT_COLOR)
    val NEW_HOVER_COLOR = JBColor.namedColor("NotificationsToolwindow.newNotification.hoverBackground", JBColor(0xE6EEF7, 0x45494A))
    val HOVER_COLOR = JBColor.namedColor("NotificationsToolwindow.Notification.hoverBackground", BG_COLOR)
    const val TIME_KEY = "TimestampKey"
  }

  val myNotificationWrapper = NotificationWrapper(notification)
  private var myIsNew = false
  private var myHoverState = false
  private val myMoreButton: Component?
  private var myMorePopupVisible = false
  private var myRoundColor = BG_COLOR
  private lateinit var myDoNotAskHandler: (Boolean) -> Unit
  private lateinit var myRemoveCallback: Consumer<Notification>

  private var myMorePopup: JBPopup? = null
  var myMoreAwtPopup: JPopupMenu? = null
  var myDropDownPopup: JPopupMenu? = null
  val myPopupAlarm = Alarm()

  private var myLafUpdater: Runnable? = null

  init {
    isOpaque = true
    background = BG_COLOR
    border = JBUI.Borders.empty(10, 10, 10, 0)

    layout = object : BorderLayout(JBUI.scale(7), 0) {
      private var myEastComponent: Component? = null

      override fun addLayoutComponent(name: String?, comp: Component) {
        if (EAST == name) {
          myEastComponent = comp
        }
        else {
          super.addLayoutComponent(name, comp)
        }
      }

      override fun layoutContainer(target: Container) {
        super.layoutContainer(target)
        if (myEastComponent != null && myEastComponent!!.isVisible) {
          val insets = target.insets
          val height = target.height - insets.bottom - insets.top
          val component = myEastComponent!!
          component.setSize(component.width, height)
          val d = component.preferredSize
          component.setBounds(target.width - insets.right - d.width, insets.top, d.width, height)
        }
      }
    }

    val iconPanel = JPanel(BorderLayout())
    iconPanel.isOpaque = false
    iconPanel.add(JBLabel(NotificationsUtil.getIcon(notification)), BorderLayout.NORTH)
    add(iconPanel, BorderLayout.WEST)

    val centerPanel = JPanel(VerticalLayout(JBUI.scale(8)))
    centerPanel.isOpaque = false
    centerPanel.border = JBUI.Borders.emptyRight(10)

    var titlePanel: JPanel? = null

    if (notification.hasTitle()) {
      val titleContent = NotificationsUtil.buildHtml(notification, null, false, null, null)
      val title = object : JBLabel(titleContent) {
        override fun updateUI() {
          val oldEditor = UIUtil.findComponentOfType(this, JEditorPane::class.java)
          if (oldEditor != null) {
            singleSelectionHandler.remove(oldEditor)
          }

          super.updateUI()

          val newEditor = UIUtil.findComponentOfType(this, JEditorPane::class.java)
          if (newEditor != null) {
            singleSelectionHandler.add(newEditor, true)
          }
        }
      }

      try {
        title.setCopyable(true)
      }
      catch (_: Exception) {
      }

      NotificationsManagerImpl.setTextAccessibleName(title, titleContent)

      val editor = UIUtil.findComponentOfType(title, JEditorPane::class.java)
      if (editor != null) {
        singleSelectionHandler.add(editor, true)
      }

      if (notification.isSuggestionType) {
        centerPanel.add(title)
      }
      else {
        titlePanel = JPanel(BorderLayout())
        titlePanel.isOpaque = false
        titlePanel.add(title)
        centerPanel.add(titlePanel)
      }
    }

    if (notification.hasContent()) {
      val textContent = NotificationsUtil.buildFullContent(notification)
      val textComponent = createTextComponent(textContent)

      NotificationsManagerImpl.setTextAccessibleName(textComponent, textContent)

      singleSelectionHandler.add(textComponent, true)

      if (!notification.hasTitle() && !notification.isSuggestionType) {
        titlePanel = JPanel(BorderLayout())
        titlePanel.isOpaque = false
        titlePanel.add(textComponent)
        centerPanel.add(titlePanel)
      }
      else {
        centerPanel.add(textComponent)
      }
    }

    val actions = notification.actions
    val actionsSize = actions.size
    val helpAction = notification.contextHelpAction

    if (actionsSize > 0 || helpAction != null) {
      val layout = HorizontalLayout(JBUIScale.scale(16))
      val actionPanel = JPanel(if (!notification.isSuggestionType && actions.size > 1) DropDownActionLayout(layout) else layout)
      actionPanel.isOpaque = false

      if (notification.isSuggestionType) {
        if (actionsSize > 0) {
          val button = JButton(actions[0].templateText)
          button.isOpaque = false
          button.addActionListener {
            runAction(actions[0], it.source)
          }
          Notification.setDataProvider(notification, button)
          actionPanel.add(button)

          if (actionsSize == 2) {
            actionPanel.add(createAction(actions[1]))
          }
          else if (actionsSize > 2) {
            actionPanel.add(MoreAction(this, actions))
          }
        }
      }
      else {
        if (actionsSize > 1 && notification.collapseDirection == Notification.CollapseActionsDirection.KEEP_RIGHTMOST) {
          actionPanel.add(MyDropDownAction(this))
        }

        for (action in actions) {
          actionPanel.add(createAction(action))
        }

        if (actionsSize > 1 && notification.collapseDirection == Notification.CollapseActionsDirection.KEEP_LEFTMOST) {
          actionPanel.add(MyDropDownAction(this))
        }
      }
      if (helpAction != null) {
        val presentation = helpAction.templatePresentation
        val helpLabel = ContextHelpLabel.create(StringUtil.defaultIfEmpty(presentation.text, ""), presentation.description)
        helpLabel.foreground = UIUtil.getLabelDisabledForeground()
        actionPanel.add(helpLabel)
      }
      if (!notification.hasTitle() && !notification.hasContent() && !notification.isSuggestionType) {
        titlePanel = JPanel(BorderLayout())
        titlePanel.isOpaque = false
        actionPanel.add(titlePanel, HorizontalLayout.RIGHT)
      }
      centerPanel.add(actionPanel)
    }

    add(centerPanel)

    if (notification.isSuggestionType) {
      val button = createPopupAction(notification)
      button.border = JBUI.Borders.emptyRight(5)
      button.isVisible = false
      myMoreButton = button

      val eastPanel = JPanel(BorderLayout())
      eastPanel.isOpaque = false
      eastPanel.add(button, BorderLayout.NORTH)
      add(eastPanel, BorderLayout.EAST)
      setComponentZOrder(eastPanel, 0)
    }
    else {
      val timeComponent = JBLabel(DateFormatUtil.formatPrettyDateTime(notification.timestamp))
      timeComponent.putClientProperty(TIME_KEY, notification.timestamp)
      timeComponent.toolTipText = DateFormatUtil.formatDateTime(notification.timestamp)
      timeComponent.border = JBUI.Borders.emptyRight(10)
      timeComponent.smallFontFunction()
      timeComponent.foreground = INFO_COLOR

      timeComponents.add(timeComponent)

      if (NotificationsConfigurationImpl.getInstanceImpl().isRegistered(notification.groupId)) {
        val button = createPopupAction(notification)
        myMoreButton = button

        val buttonWrapper = JPanel(BorderLayout())
        buttonWrapper.isOpaque = false
        buttonWrapper.border = JBUI.Borders.emptyRight(10)
        buttonWrapper.add(button, BorderLayout.NORTH)
        buttonWrapper.preferredSize = buttonWrapper.preferredSize

        button.isVisible = false

        val eastPanel = JPanel(BorderLayout())
        eastPanel.isOpaque = false
        eastPanel.add(buttonWrapper, BorderLayout.WEST)
        eastPanel.add(timeComponent, BorderLayout.EAST)
        titlePanel!!.add(eastPanel, BorderLayout.EAST)
      }
      else {
        titlePanel!!.add(timeComponent, BorderLayout.EAST)
        myMoreButton = null
      }
    }
  }

  private class MyActionGroup : DefaultActionGroup(), TooltipDescriptionProvider {
    init {
      isPopup = true
    }
  }

  private fun createAction(action: AnAction): JComponent {
    return object : LinkLabel<AnAction>(action.templateText, action.templatePresentation.icon,
                                        { link, _action -> runAction(_action, link) }, action) {
      init {
        Notification.setDataProvider(myNotificationWrapper.notification!!, this)
      }

      override fun getTextColor() = JBUI.CurrentTheme.Link.Foreground.ENABLED
    }
  }

  private fun createPopupAction(notification: Notification): JComponent {
    val group = MyActionGroup()

    if (NotificationsConfigurationImpl.getInstanceImpl().isRegistered(notification.groupId)) {
      group.add(object : DumbAwareAction(IdeBundle.message("notification.settings.action.text")) {
        override fun actionPerformed(e: AnActionEvent) {
          doShowSettings()
        }
      })
      group.addSeparator()
    }

    if (notification.isSuggestionType) {
      val remindAction = RemindLaterManager.createAction(notification, DateFormatUtil.DAY)
      if (remindAction != null) {
        @Suppress("DialogTitleCapitalization")
        group.add(object : DumbAwareAction(IdeBundle.message("notifications.toolwindow.remind.tomorrow")) {
          override fun actionPerformed(e: AnActionEvent) {
            remindAction.run()
            myRemoveCallback.accept(myNotificationWrapper.notification!!)
            myNotificationWrapper.notification!!.hideBalloon()
          }
        })
      }
    }

    @Suppress("DialogTitleCapitalization")
    group.add(object : DumbAwareAction(IdeBundle.message("notifications.toolwindow.dont.show.again.for.this.project")) {
      override fun actionPerformed(e: AnActionEvent) {
        myDoNotAskHandler.invoke(true)
      }
    })
    @Suppress("DialogTitleCapitalization")
    group.add(object : DumbAwareAction(IdeBundle.message("notifications.toolwindow.dont.show.again")) {
      override fun actionPerformed(e: AnActionEvent) {
        myDoNotAskHandler.invoke(false)
      }
    })

    val presentation = Presentation()
    presentation.description = IdeBundle.message("tooltip.turn.notification.off")
    presentation.icon = AllIcons.Actions.More
    presentation.putClientProperty(ActionButton.HIDE_DROPDOWN_ICON, java.lang.Boolean.TRUE)

    val button = object : ActionButton(group, presentation, ActionPlaces.UNKNOWN, ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE) {
      override fun createAndShowActionGroupPopup(actionGroup: ActionGroup, event: AnActionEvent): JBPopup {
        myMorePopupVisible = true
        val popup = super.createAndShowActionGroupPopup(actionGroup, event)
        myMorePopup = popup
        popup.addListener(object : JBPopupListener {
          override fun onClosed(event: LightweightWindowEvent) {
            myMorePopup = null
            ApplicationManager.getApplication().invokeLater {
              myMorePopupVisible = false
              isVisible = myHoverState
            }
          }
        })
        return popup
      }
    }

    return button
  }

  private fun doShowSettings() {
    NotificationCollector.getInstance().logNotificationSettingsClicked(myNotificationWrapper.id, myNotificationWrapper.displayId,
                                                                       myNotificationWrapper.groupId)
    val configurable = NotificationsConfigurable()
    ShowSettingsUtil.getInstance().editConfigurable(project, configurable, Runnable {
      val runnable = configurable.enableSearch(myNotificationWrapper.groupId)
      runnable?.run()
    })
  }

  private fun runAction(action: AnAction, component: Any) {
    setNew(false)
    NotificationCollector.getInstance().logNotificationActionInvoked(null, myNotificationWrapper.notification!!, action,
                                                                     NotificationCollector.NotificationPlace.ACTION_CENTER)
    Notification.fire(myNotificationWrapper.notification!!, action, DataManager.getInstance().getDataContext(component as Component))
  }

  fun expire() {
    closePopups()
    myNotificationWrapper.notification = null
    setNew(false)

    for (component in UIUtil.findComponentsOfType(this, LinkLabel::class.java)) {
      component.isEnabled = false
    }

    val dropDownAction = UIUtil.findComponentOfType(this, MyDropDownAction::class.java)
    if (dropDownAction != null) {
      DataManager.removeDataProvider(dropDownAction)
    }

    if (myMoreButton != null) {
      myMoreButton.isVisible = false
    }
  }

  fun removeFromParent() {
    closePopups()
    for (component in UIUtil.findComponentsOfType(this, JTextComponent::class.java)) {
      singleSelectionHandler.remove(component)
    }
  }

  private fun closePopups() {
    myMorePopup?.cancel()
    myMoreAwtPopup?.isVisible = false
    myDropDownPopup?.isVisible = false
    Disposer.dispose(myPopupAlarm)
  }

  private fun createTextComponent(text: @Nls String): JEditorPane {
    val component = JEditorPane()
    component.isEditable = false
    component.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, java.lang.Boolean.TRUE)
    component.contentType = "text/html"
    component.isOpaque = false
    component.border = null

    NotificationsUtil.configureHtmlEditorKit(component, false)

    if (myNotificationWrapper.notification!!.listener != null) {
      component.addHyperlinkListener { e ->
        val notification = myNotificationWrapper.notification
        if (notification != null && e.eventType == HyperlinkEvent.EventType.ACTIVATED) {
          val listener = notification.listener
          if (listener != null) {
            NotificationCollector.getInstance().logHyperlinkClicked(notification)
            listener.hyperlinkUpdate(notification, e)
          }
        }
      }
    }

    component.putClientProperty(AccessibleContext.ACCESSIBLE_NAME_PROPERTY, StringUtil.unescapeXmlEntities(StringUtil.stripHtml(text, " ")))

    component.text = text

    component.isEditable = false
    if (component.caret != null) {
      component.caretPosition = 0
    }

    myLafUpdater = Runnable {
      NotificationsUtil.configureHtmlEditorKit(component, false)
      component.text = text
      component.revalidate()
      component.repaint()
    }

    return component
  }

  fun updateLaf() {
    myLafUpdater?.run()
    updateColor()
  }

  fun setDoNotAskHandler(handler: (Boolean) -> Unit) {
    myDoNotAskHandler = handler
  }

  fun setRemoveCallback(callback: Consumer<Notification>) {
    myRemoveCallback = callback
  }

  fun isHover(): Boolean = myHoverState

  fun setNew(state: Boolean) {
    if (myIsNew != state) {
      myIsNew = state
      updateColor()
    }
  }

  fun setHover(state: Boolean) {
    myHoverState = state
    if (myMoreButton != null) {
      if (!myMorePopupVisible) {
        myMoreButton.isVisible = state && myNotificationWrapper.notification != null
      }
    }
    updateColor()
  }

  private fun updateColor() {
    if (myHoverState) {
      if (myIsNew) {
        setColor(NEW_HOVER_COLOR)
      }
      else {
        setColor(HOVER_COLOR)
      }
    }
    else if (myIsNew) {
      if (UIManager.getColor(NEW_COLOR_NAME) != null) {
        setColor(NEW_COLOR)
      }
      else {
        setColor(NEW_DEFAULT_COLOR)
      }
    }
    else {
      setColor(BG_COLOR)
    }
  }

  private fun setColor(color: Color) {
    myRoundColor = color
    repaint()
  }

  override fun paintComponent(g: Graphics) {
    super.paintComponent(g)
    if (myRoundColor !== BG_COLOR) {
      g.color = myRoundColor
      val config = GraphicsUtil.setupAAPainting(g)
      val cornerRadius = NotificationBalloonRoundShadowBorderProvider.CORNER_RADIUS.get()
      g.fillRoundRect(0, 0, width, height, cornerRadius, cornerRadius)
      config.restore()
    }
  }

  fun applySearchQuery(query: String?): Boolean {
    if (query == null) {
      isVisible = true
      return true
    }

    val result = matchQuery(query)
    isVisible = result
    return result
  }

  private fun matchQuery(query: @NlsSafe String): Boolean {
    if (myNotificationWrapper.title.contains(query, true)) {
      return true
    }
    val subtitle = myNotificationWrapper.subtitle
    if (subtitle != null && subtitle.contains(query, true)) {
      return true
    }
    if (myNotificationWrapper.content.contains(query, true)) {
      return true
    }
    for (action in myNotificationWrapper.actions) {
      if (action != null && action.contains(query, true)) {
        return true
      }
    }
    return false
  }
}

private class MoreAction(val notificationComponent: NotificationComponent, actions: List<AnAction>) :
  NotificationsManagerImpl.DropDownAction(null, null) {
  val group = DefaultActionGroup()

  init {
    val size = actions.size
    for (i in 1..size - 1) {
      group.add(actions[i])
    }

    setListener(LinkListener { link, _ ->
      if (notificationComponent.myMoreAwtPopup != null) {
        notificationComponent.myMoreAwtPopup!!.isVisible = false
        notificationComponent.myMoreAwtPopup = null
        return@LinkListener
      }

      notificationComponent.myPopupAlarm.cancelAllRequests()

      val popup = NotificationsManagerImpl.showPopup(link, group)
      notificationComponent.myMoreAwtPopup = popup
      popup?.addPopupMenuListener(object : PopupMenuListenerAdapter() {
        override fun popupMenuWillBecomeInvisible(e: PopupMenuEvent?) {
          notificationComponent.myPopupAlarm.addRequest(Runnable { notificationComponent.myMoreAwtPopup = null }, 500)
        }
      })
    }, null)

    text = IdeBundle.message("notifications.action.more")

    Notification.setDataProvider(notificationComponent.myNotificationWrapper.notification!!, this)
  }

  override fun getTextColor() = JBUI.CurrentTheme.Link.Foreground.ENABLED
}

private class MyDropDownAction(val notificationComponent: NotificationComponent) : NotificationsManagerImpl.DropDownAction(null, null) {
  var collapseActionsDirection: Notification.CollapseActionsDirection = notificationComponent.myNotificationWrapper.notification!!.collapseDirection

  init {
    setListener(LinkListener { link, _ ->
      if (notificationComponent.myDropDownPopup != null) {
        notificationComponent.myDropDownPopup!!.isVisible = false
        notificationComponent.myDropDownPopup = null
        return@LinkListener
      }

      val group = DefaultActionGroup()
      val layout = link.parent.layout as DropDownActionLayout

      for (action in layout.actions) {
        if (!action.isVisible) {
          group.add(action.linkData)
        }
      }

      notificationComponent.myPopupAlarm.cancelAllRequests()

      val popup = NotificationsManagerImpl.showPopup(link, group)
      notificationComponent.myDropDownPopup = popup
      popup?.addPopupMenuListener(object : PopupMenuListenerAdapter() {
        override fun popupMenuWillBecomeInvisible(e: PopupMenuEvent?) {
          notificationComponent.myPopupAlarm.addRequest(Runnable { notificationComponent.myDropDownPopup = null }, 500)
        }
      })
    }, null)

    text = notificationComponent.myNotificationWrapper.notification!!.dropDownText
    isVisible = false

    Notification.setDataProvider(notificationComponent.myNotificationWrapper.notification!!, this)
  }

  override fun getTextColor() = JBUI.CurrentTheme.Link.Foreground.ENABLED
}

private class NotificationWrapper(notification: Notification) {
  val title = notification.title
  val subtitle = notification.subtitle
  val content = notification.content
  val id = notification.id
  val displayId = notification.displayId
  val groupId = notification.groupId
  val actions: List<String?> = notification.actions.stream().map { it.templateText }.toList()
  var notification: Notification? = notification
}

private class DropDownActionLayout(layout: LayoutManager2) : FinalLayoutWrapper(layout) {
  val actions = ArrayList<LinkLabel<AnAction>>()
  private lateinit var myDropDownAction: MyDropDownAction

  override fun addLayoutComponent(comp: Component, constraints: Any?) {
    super.addLayoutComponent(comp, constraints)
    add(comp)
  }

  override fun addLayoutComponent(name: String?, comp: Component) {
    super.addLayoutComponent(name, comp)
    add(comp)
  }

  private fun add(component: Component) {
    if (component is MyDropDownAction) {
      myDropDownAction = component
    }
    else if (component is LinkLabel<*>) {
      @Suppress("UNCHECKED_CAST")
      actions.add(component as LinkLabel<AnAction>)
    }
  }

  override fun layoutContainer(parent: Container) {
    val width = parent.width

    myDropDownAction.isVisible = false
    for (action in actions) {
      action.isVisible = true
    }
    layout.layoutContainer(parent)

    val keepRightmost = myDropDownAction.collapseActionsDirection == Notification.CollapseActionsDirection.KEEP_RIGHTMOST
    val collapseStart = if (keepRightmost) 0 else actions.size - 1
    val collapseDelta = if (keepRightmost) 1 else -1
    var collapseIndex = collapseStart

    if (parent.preferredSize.width > width) {
      myDropDownAction.isVisible = true
      actions[collapseIndex].isVisible = false
      collapseIndex += collapseDelta
      actions[collapseIndex].isVisible = false
      collapseIndex += collapseDelta
      layout.layoutContainer(parent)

      while (parent.preferredSize.width > width && collapseIndex >= 0 && collapseIndex < actions.size) {
        actions[collapseIndex].isVisible = false
        collapseIndex += collapseDelta
        layout.layoutContainer(parent)
      }
    }
  }
}

private class ComponentEventHandler {
  private var myHoverComponent: NotificationComponent? = null

  private val myMouseHandler = object : MouseAdapter() {
    override fun mouseMoved(e: MouseEvent) {
      if (myHoverComponent == null) {
        val component = ComponentUtil.getParentOfType(NotificationComponent::class.java, e.component)
        if (component != null) {
          if (!component.isHover()) {
            component.setHover(true)
          }
          myHoverComponent = component
        }
      }
    }

    override fun mouseExited(e: MouseEvent) {
      if (myHoverComponent != null) {
        val component = myHoverComponent!!
        if (component.isHover()) {
          component.setHover(false)
        }
        myHoverComponent = null
      }
    }
  }

  fun add(component: Component) {
    addAll(component) { c ->
      c.addMouseListener(myMouseHandler)
      c.addMouseMotionListener(myMouseHandler)
    }
  }

  private fun addAll(component: Component, listener: (Component) -> Unit) {
    listener.invoke(component)

    if (component is JBOptionButton) {
      component.addContainerListener(object : ContainerAdapter() {
        override fun componentAdded(e: ContainerEvent) {
          addAll(e.child, listener)
        }
      })
    }

    for (child in UIUtil.uiChildren(component)) {
      addAll(child, listener)
    }
  }
}

internal class ApplicationNotificationModel {
  private val myNotifications = ArrayList<Notification>()
  private val myProjectToModel = HashMap<Project, ProjectNotificationModel>()
  private val myLock = Object()

  internal fun registerAndGetInitNotifications(content: NotificationContent, notifications: MutableList<Notification>) {
    synchronized(myLock) {
      notifications.addAll(myNotifications)
      myNotifications.clear()

      val model = myProjectToModel.getOrPut(content.project) { ProjectNotificationModel() }
      model.registerAndGetInitNotifications(content, notifications)
    }
  }

  internal fun unregister(content: NotificationContent) {
    synchronized(myLock) {
      myProjectToModel.remove(content.project)
    }
  }

  fun addNotification(project: Project?, notification: Notification) {
    val runnables = ArrayList<Runnable>()

    synchronized(myLock) {
      if (project == null) {
        if (myProjectToModel.isEmpty()) {
          myNotifications.add(notification)
        }
        else {
          for ((_project, model) in myProjectToModel.entries) {
            model.addNotification(_project, notification, myNotifications, runnables)
          }
        }
      }
      else {
        val model = myProjectToModel.getOrPut(project) {
          Disposer.register(project) {
            synchronized(myLock) {
              myProjectToModel.remove(project)
            }
          }
          ProjectNotificationModel()
        }
        model.addNotification(project, notification, myNotifications, runnables)
      }
    }

    for (runnable in runnables) {
      runnable.run()
    }
  }

  fun getStateNotifications(project: Project): List<Notification> {
    synchronized(myLock) {
      val model = myProjectToModel[project]
      if (model != null) {
        return model.getStateNotifications()
      }
    }
    return emptyList()
  }

  fun getNotifications(project: Project?): List<Notification> {
    synchronized(myLock) {
      if (project == null) {
        return ArrayList(myNotifications)
      }
      val model = myProjectToModel[project]
      if (model == null) {
        return ArrayList(myNotifications)
      }
      return model.getNotifications(myNotifications)
    }
  }

  fun isEmptyContent(project: Project): Boolean {
    val model = myProjectToModel[project]
    return model == null || model.isEmptyContent()
  }

  fun expire(notification: Notification) {
    val runnables = ArrayList<Runnable>()

    synchronized(myLock) {
      myNotifications.remove(notification)
      for ((project, model) in myProjectToModel) {
        model.expire(project, notification, runnables)
      }
    }

    for (runnable in runnables) {
      runnable.run()
    }
  }

  fun expireAll() {
    val notifications = ArrayList<Notification>()
    val runnables = ArrayList<Runnable>()

    synchronized(myLock) {
      notifications.addAll(myNotifications)
      myNotifications.clear()
      for ((project, model) in myProjectToModel) {
        model.expireAll(project, notifications, runnables)
      }
    }

    for (runnable in runnables) {
      runnable.run()
    }

    for (notification in notifications) {
      notification.expire()
    }
  }

  fun clearAll(project: Project?) {
    synchronized(myLock) {
      myNotifications.clear()
      if (project != null) {
        myProjectToModel[project]?.clearAll(project)
      }
    }
  }

  fun getStatusMessage(project: Project): StatusMessage? {
    synchronized(myLock) {
      return myProjectToModel[project]?.getStatusMessage()
    }
  }

  fun setStatusMessage(project: Project, notification: Notification?) {
    synchronized(myLock) {
      myProjectToModel[project]?.setStatusMessage(project, notification)
    }
  }
}

private class ProjectNotificationModel {
  private val myNotifications = ArrayList<Notification>()
  private var myContent: NotificationContent? = null
  private var myStatusMessage: StatusMessage? = null

  fun registerAndGetInitNotifications(content: NotificationContent, notifications: MutableList<Notification>) {
    notifications.addAll(myNotifications)
    myNotifications.clear()
    myContent = content
  }

  fun addNotification(project: Project,
                      notification: Notification,
                      appNotifications: List<Notification>,
                      runnables: MutableList<Runnable>) {
    if (myContent == null) {
      myNotifications.add(notification)

      val notifications = ArrayList(appNotifications)
      notifications.addAll(myNotifications)

      runnables.add(Runnable {
        updateToolWindow(project, notification, notifications, false)
      })
    }
    else {
      runnables.add(Runnable { UIUtil.invokeLaterIfNeeded { myContent!!.add(notification) } })
    }
  }

  fun getStateNotifications(): List<Notification> {
    if (myContent == null) {
      return emptyList()
    }
    return myContent!!.getStateNotifications()
  }

  fun isEmptyContent(): Boolean {
    return myContent == null || myContent!!.isEmpty()
  }

  fun getNotifications(appNotifications: List<Notification>): List<Notification> {
    if (myContent == null) {
      val notifications = ArrayList(appNotifications)
      notifications.addAll(myNotifications)
      return notifications
    }
    return myContent!!.getNotifications()
  }

  fun expire(project: Project, notification: Notification, runnables: MutableList<Runnable>) {
    myNotifications.remove(notification)
    if (myContent == null) {
      runnables.add(Runnable {
        updateToolWindow(project, null, myNotifications, false)
      })
    }
    else {
      runnables.add(Runnable { UIUtil.invokeLaterIfNeeded { myContent!!.expire(notification) } })
    }
  }

  fun expireAll(project: Project, notifications: MutableList<Notification>, runnables: MutableList<Runnable>) {
    notifications.addAll(myNotifications)
    myNotifications.clear()
    if (myContent == null) {
      updateToolWindow(project, null, emptyList(), false)
    }
    else {
      runnables.add(Runnable { UIUtil.invokeLaterIfNeeded { myContent!!.expire(null) } })
    }
  }

  fun clearAll(project: Project) {
    myNotifications.clear()
    if (myContent == null) {
      updateToolWindow(project, null, emptyList(), true)
    }
    else {
      UIUtil.invokeLaterIfNeeded { myContent!!.clearAll() }
    }
  }

  private fun updateToolWindow(project: Project,
                               stateNotification: Notification?,
                               notifications: List<Notification>,
                               closeBalloons: Boolean) {
    UIUtil.invokeLaterIfNeeded {
      if (project.isDisposed) {
        return@invokeLaterIfNeeded
      }

      setStatusMessage(project, stateNotification)

      if (closeBalloons) {
        project.closeAllBalloons()
      }

      val toolWindow = ActionCenter.getToolWindow(project)
      toolWindow?.setIcon(IdeNotificationArea.getActionCenterNotificationIcon(notifications))
    }
  }

  fun getStatusMessage(): StatusMessage? {
    return myStatusMessage
  }

  fun setStatusMessage(project: Project, notification: Notification?) {
    if ((myStatusMessage == null && notification == null) || (myStatusMessage != null && myStatusMessage!!.notification === notification)) {
      return
    }
    myStatusMessage = if (notification == null) {
      null
    }
    else {
      StatusMessage(notification, NotificationsUtil.buildStatusMessage(notification), notification.timestamp)
    }
    StatusBar.Info.set("", project, ActionCenter.EVENT_REQUESTOR)
  }
}

fun Project.closeAllBalloons() {
  val ideFrame = WindowManager.getInstance().getIdeFrame(this)
  val balloonLayout = ideFrame!!.balloonLayout as BalloonLayoutImpl
  balloonLayout.closeAll()
}

private class ClearAllNotificationsAction : DumbAwareAction(IdeBundle.message("clear.all.notifications"), null, AllIcons.Actions.GC) {
  override fun update(e: AnActionEvent) {
    val project = e.project
    e.presentation.isEnabled = NotificationsToolWindowFactory.getNotifications(project).isNotEmpty() ||
                               (project != null && !NotificationsToolWindowFactory.myModel.isEmptyContent(project))
  }

  override fun getActionUpdateThread() = ActionUpdateThread.EDT

  override fun actionPerformed(e: AnActionEvent) {
    NotificationsToolWindowFactory.clearAll(e.project)
  }
}