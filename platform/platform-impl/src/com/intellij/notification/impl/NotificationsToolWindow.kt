// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.notification.impl

import com.intellij.UtilBundle
import com.intellij.codeInsight.hint.HintUtil
import com.intellij.icons.AllIcons
import com.intellij.ide.DataManager
import com.intellij.ide.IdeBundle
import com.intellij.ide.ui.LafManager
import com.intellij.ide.ui.LafManagerListener
import com.intellij.ide.util.PropertiesComponent
import com.intellij.idea.ActionsBundle
import com.intellij.notification.ActionCenter
import com.intellij.notification.EventLog
import com.intellij.notification.LogModel
import com.intellij.notification.Notification
import com.intellij.notification.impl.ui.NotificationsUtil
import com.intellij.notification.impl.widget.IdeNotificationArea
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Divider
import com.intellij.openapi.ui.NullableComponent
import com.intellij.openapi.ui.OnePixelDivider
import com.intellij.openapi.ui.ex.MultiLineLabel
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupListener
import com.intellij.openapi.ui.popup.LightweightWindowEvent
import com.intellij.openapi.util.Clock
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.WindowManager
import com.intellij.openapi.wm.ex.ToolWindowEx
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import com.intellij.ui.*
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBOptionButton
import com.intellij.ui.components.JBPanelWithEmptyText
import com.intellij.ui.components.JBScrollPane
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
import java.util.*
import java.util.function.Consumer
import javax.accessibility.AccessibleContext
import javax.swing.*
import javax.swing.event.DocumentEvent
import javax.swing.text.JTextComponent

/**
 * @author Alexander Lobas
 */

class NotificationsToolWindowFactory : ToolWindowFactory, DumbAware {
  companion object {
    const val ID = "Notifications"

    internal val myModel = ApplicationNotificationModel()

    fun addNotification(project: Project?, notification: Notification) {
      if (ActionCenter.isEnabled() && notification.canShowFor(project)) {
        myModel.addNotification(project, notification)
      }
    }

    fun expire(notification: Notification) {
      myModel.expire(notification)
    }

    fun expireAll() {
      myModel.expireAll()
    }

    fun getStateNotifications(project: Project) = myModel.getStateNotifications(project)

    fun getNotifications(project: Project?) = myModel.getNotifications(project)
  }

  override fun isApplicable(project: Project): Boolean {
    return ActionCenter.isEnabled()
  }

  override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
    NotificationContent(project, toolWindow)
  }
}

internal class NotificationContent(val project: Project,
                                   val toolWindow: ToolWindow) : Disposable, ToolWindowManagerListener, LafManagerListener {
  private val myMainPanel = JBPanelWithEmptyText(BorderLayout())

  private val myNotifications = ArrayList<Notification>()
  private val myIconNotifications = ArrayList<Notification>()

  private val suggestions: NotificationGroupComponent
  private val timeline: NotificationGroupComponent
  private val searchController: SearchController

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

    myMainPanel.add(createSearchComponent(toolWindow), BorderLayout.NORTH)

    val splitter = MySplitter()
    splitter.firstComponent = suggestions
    splitter.secondComponent = timeline
    myMainPanel.add(splitter)

    suggestions.setRemoveCallback(Consumer(::remove))
    timeline.setClearCallback(::clear)

    Disposer.register(toolWindow.disposable, this)

    val content = ContentFactory.SERVICE.getInstance().createContent(myMainPanel, "", false)
    content.preferredFocusableComponent = myMainPanel

    val contentManager = toolWindow.contentManager
    contentManager.addContent(content)
    contentManager.setSelectedContent(content)

    project.messageBus.connect(toolWindow.disposable).subscribe(ToolWindowManagerListener.TOPIC, this)

    ApplicationManager.getApplication().messageBus.connect(toolWindow.disposable).subscribe(LafManagerListener.TOPIC, this)

    val newNotifications = ArrayList<Notification>()
    NotificationsToolWindowFactory.myModel.registerAndGetInitNotifications(this, newNotifications)
    for (notification in newNotifications) {
      add(notification)
    }
  }

  private fun createSearchComponent(toolWindow: ToolWindow): SearchTextField {
    val searchField = object : SearchTextField() {
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

    searchController.searchField = searchField

    searchField.addDocumentListener(object : DocumentAdapter() {
      override fun textChanged(e: DocumentEvent) {
        mySearchUpdateAlarm.cancelAllRequests()
        mySearchUpdateAlarm.addRequest(searchController::doSearch, 100, ModalityState.stateForComponent(searchField))
      }
    })

    val gearAction = object : DumbAwareAction() {
      override fun actionPerformed(e: AnActionEvent) {
        searchField.isVisible = true
        searchField.selectText()
        searchField.requestFocus()
        searchController.startSearch()
      }
    }

    val findAction = ActionManager.getInstance().getAction(IdeActions.ACTION_FIND)
    if (findAction == null) {
      gearAction.templatePresentation.text = ActionsBundle.actionText(IdeActions.ACTION_FIND)
    }
    else {
      gearAction.copyFrom(findAction)
      gearAction.registerCustomShortcutSet(findAction.shortcutSet, myMainPanel)
    }

    (toolWindow as ToolWindowEx).setAdditionalGearActions(DefaultActionGroup(gearAction))

    return searchField
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
    setStatusMessage(notification)
    updateIcon()
  }

  fun getStateNotifications() = ArrayList(myIconNotifications)

  fun getNotifications() = ArrayList(myNotifications)

  fun expire(notification: Notification?) {
    if (notification == null) {
      val notifications = ArrayList(myNotifications)

      myNotifications.clear()
      myIconNotifications.clear()
      suggestions.clear()
      timeline.clear()
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
    setStatusMessage()
    updateIcon()
  }

  private fun clear(notifications: List<Notification>) {
    myNotifications.removeAll(notifications)
    myIconNotifications.removeAll(notifications)
    searchController.update()
    setStatusMessage()
    updateIcon()
  }

  override fun stateChanged(toolWindowManager: ToolWindowManager) {
    val visible = toolWindow.isVisible
    if (myVisible != visible) {
      myVisible = visible
      if (visible) {
        val ideFrame = WindowManager.getInstance().getIdeFrame(project)
        val balloonLayout = ideFrame!!.balloonLayout as BalloonLayoutImpl
        balloonLayout.closeAll()

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

  override fun lookAndFeelChanged(source: LafManager) {
    ApplicationManager.getApplication().invokeLater {
      suggestions.updateLaf()
      timeline.updateLaf()
    }
  }

  private fun updateIcon() {
    toolWindow.setIcon(IdeNotificationArea.getActionCenterNotificationIcon(myIconNotifications))
    LogModel.fireModelChanged()
  }

  private fun setStatusMessage() {
    setStatusMessage(myNotifications.findLast { it.isImportant || it.isImportantSuggestion })
  }

  private fun setStatusMessage(notification: Notification?) {
    EventLog.getLogModel(project).setStatusMessage(notification)
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
  font = JBFont.small()
  val f: (JComponent) -> Unit = {
    it.font = JBFont.small()
  }
  putClientProperty(NotificationGroupComponent.FONT_KEY, f)
}

private class SearchController(private val mainContent: NotificationContent,
                               private val suggestions: NotificationGroupComponent,
                               private val timeline: NotificationGroupComponent) {
  lateinit var searchField: SearchTextField

  fun startSearch() {
    mainContent.clearEmptyState()

    if (searchField.text.isNotEmpty()) {
      doSearch()
    }
  }

  fun doSearch() {
    val query = searchField.text

    if (query.isEmpty()) {
      searchField.textEditor.background = UIUtil.getTextFieldBackground()
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
    searchField.textEditor.background = if (result) UIUtil.getTextFieldBackground() else LightColors.RED
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

private class NotificationGroupComponent(private val myMainContent: NotificationContent,
                                         private val mySuggestionType: Boolean,
                                         private val myProject: Project) :
  JPanel(BorderLayout()), NullableComponent {

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

  private val mySuggestionGotItPanel = JPanel(BorderLayout())

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
      mySuggestionGotItPanel.background = JBColor.lazy {
        EditorColorsManager.getInstance().globalScheme.getColor(HintUtil.PROMOTION_PANE_KEY)
      }
      mySuggestionGotItPanel.isVisible = false
      mySuggestionGotItPanel.border = JBUI.Borders.customLineBottom(JBColor.border())
      add(mySuggestionGotItPanel, BorderLayout.NORTH)

      val gotItTitle = MultiLineLabel(IdeBundle.message("notifications.toolwindow.suggestion.gotit.title"))
      gotItTitle.mediumFontFunction()
      gotItTitle.border = JBUI.Borders.empty(7, 12, 7, 0)
      mySuggestionGotItPanel.add(gotItTitle, BorderLayout.WEST)

      val panel = JPanel(BorderLayout())
      panel.isOpaque = false
      panel.border = JBUI.Borders.empty(7, 0, 0, 12)
      mySuggestionGotItPanel.add(panel, BorderLayout.EAST)
      panel.add(LinkLabel<Any>(IdeBundle.message("notifications.toolwindow.suggestion.gotit.link"), null) { _, _ ->
        mySuggestionGotItPanel.isVisible = false
        myTitle.isVisible = true
        myMainContent.fullRepaint()
      }, BorderLayout.NORTH)

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
    val component = NotificationComponent(notification, myTimeComponents, singleSelectionHandler)
    component.setNew(true)

    myList.add(component, 0)
    updateLayout()
    myEventHandler.add(component)

    if (mySuggestionType && !PropertiesComponent.getInstance().getBoolean("notification.suggestion.dont.show.gotit")) {
      PropertiesComponent.getInstance().setValue("notification.suggestion.dont.show.gotit", true)
      mySuggestionGotItPanel.isVisible = true
      myTitle.isVisible = false
    }

    updateContent()

    if (mySuggestionType) {
      component.setDoNotAskHandler { forProject ->
        component.notification.setDoNotAsFor(if (forProject) myProject else null)
        myRemoveCallback.accept(component.notification)
        component.notification.hideBalloon()
      }

      component.setRemoveCallback(myRemoveCallback)
    }
  }

  private fun updateLayout() {
    val layout = myList.layout
    iterateComponents { component ->
      layout.removeLayoutComponent(component)
      layout.addLayoutComponent(null, component)
    }
  }

  fun setRemoveCallback(callback: Consumer<Notification>) {
    myRemoveCallback = callback
  }

  fun remove(notification: Notification) {
    val count = myList.componentCount
    for (i in 0 until count) {
      val component = myList.getComponent(i) as NotificationComponent
      if (component.notification === notification) {
        component.removeFromParent()
        myList.remove(i)
        break
      }
    }
    updateContent()
  }

  fun setClearCallback(callback: (List<Notification>) -> Unit) {
    myClearCallback = callback
  }

  private fun clearAll() {
    val ideFrame = WindowManager.getInstance().getIdeFrame(myProject)
    val balloonLayout = ideFrame!!.balloonLayout as BalloonLayoutImpl
    balloonLayout.closeAll()

    val notifications = ArrayList<Notification>()
    iterateComponents { notifications.add(it.notification) }
    clear()
    myClearCallback.invoke(notifications)
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

private class NotificationComponent(val notification: Notification,
                                    timeComponents: ArrayList<JLabel>,
                                    val singleSelectionHandler: SingleTextSelectionHandler) : JPanel() {

  companion object {
    val BG_COLOR = UIUtil.getListBackground()
    val INFO_COLOR = JBColor.namedColor("Label.infoForeground", JBColor(Gray.x80, Gray.x8C))
    val NEW_COLOR = JBColor.namedColor("NotificationsToolwindow.newNotification.background", JBColor(0xE6EEF7, 0x45494A))
    val NEW_HOVER_COLOR = JBColor.namedColor("NotificationsToolwindow.newNotification.hoverBackground", JBColor(0xE6EEF7, 0x45494A))
    val HOVER_COLOR = JBColor.namedColor("NotificationsToolwindow.Notification.hoverBackground", BG_COLOR)
    const val TIME_KEY = "TimestampKey"
  }

  private val myBgComponents = ArrayList<Component>()
  private var myIsNew = false
  private var myHoverState = false
  private val myMoreButton: Component?
  private var myMorePopupVisible = false
  private var myRoundColor = BG_COLOR
  private lateinit var myDoNotAskHandler: (Boolean) -> Unit
  private lateinit var myRemoveCallback: Consumer<Notification>
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
      }.setCopyable(true)

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
      val textContent = NotificationsUtil.buildHtml(notification, null, true, null, null)
      val text = createTextComponent(textContent)

      NotificationsManagerImpl.setTextAccessibleName(text, textContent)

      singleSelectionHandler.add(text, true)

      if (!notification.hasTitle() && !notification.isSuggestionType) {
        titlePanel = JPanel(BorderLayout())
        titlePanel.isOpaque = false
        titlePanel.add(text)
        centerPanel.add(titlePanel)
      }
      else {
        centerPanel.add(text)
      }
    }

    val actions = notification.actions
    val actionsSize = actions.size

    if (actionsSize > 0) {
      val layout = HorizontalLayout(JBUIScale.scale(16))
      val actionPanel = JPanel(if (!notification.isSuggestionType && actions.size > 1) DropDownActionLayout(layout) else layout)
      actionPanel.isOpaque = false

      if (notification.isSuggestionType) {
        if (actionsSize == 1) {
          val button = JButton(actions[0].templateText)
          button.isOpaque = false
          button.addActionListener {
            runAction(actions[0], it.source)
          }
          actionPanel.add(button)
        }
        else if (actionsSize > 1) {
          val buttonActions = Array(actionsSize - 1) { i -> createAction(actions[i + 1]) }
          val optionButton = NotificationOptionButton(createAction(actions[0]), buttonActions)
          optionButton.background = BG_COLOR
          actionPanel.add(optionButton)
          myBgComponents.add(optionButton)
        }
      }
      else {
        if (actionsSize > 1 && notification.collapseDirection == Notification.CollapseActionsDirection.KEEP_RIGHTMOST) {
          actionPanel.add(MyDropDownAction(this))
        }

        for (action in actions) {
          @Suppress("DialogTitleCapitalization")
          actionPanel.add(
            LinkLabel(action.templateText, action.templatePresentation.icon, { link, _action -> runAction(_action, link) }, action))
        }

        if (actionsSize > 1 && notification.collapseDirection == Notification.CollapseActionsDirection.KEEP_LEFTMOST) {
          actionPanel.add(MyDropDownAction(this))
        }
      }
      centerPanel.add(actionPanel)
    }

    add(centerPanel)

    if (notification.isSuggestionType) {
      val group = DefaultActionGroup()
      group.isPopup = true

      val remindAction = RemindLaterManager.createAction(notification, DateFormatUtil.DAY)
      if (remindAction != null) {
        @Suppress("DialogTitleCapitalization")
        group.add(object : AnAction(IdeBundle.message("notifications.toolwindow.remind.tomorrow")) {
          override fun actionPerformed(e: AnActionEvent) {
            remindAction.run()
            myRemoveCallback.accept(notification)
            notification.hideBalloon()
          }
        })
      }

      @Suppress("DialogTitleCapitalization")
      group.add(object : AnAction(IdeBundle.message("notifications.toolwindow.dont.show.again.for.this.project")) {
        override fun actionPerformed(e: AnActionEvent) {
          myDoNotAskHandler.invoke(true)
        }
      })
      @Suppress("DialogTitleCapitalization")
      group.add(object : AnAction(IdeBundle.message("notifications.toolwindow.dont.show.again")) {
        override fun actionPerformed(e: AnActionEvent) {
          myDoNotAskHandler.invoke(false)
        }
      })

      val presentation = Presentation()
      presentation.icon = AllIcons.Actions.More
      presentation.putClientProperty(ActionButton.HIDE_DROPDOWN_ICON, java.lang.Boolean.TRUE)

      val button = object : ActionButton(group, presentation, ActionPlaces.UNKNOWN, ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE) {
        override fun createAndShowActionGroupPopup(actionGroup: ActionGroup, event: AnActionEvent): JBPopup {
          myMorePopupVisible = true
          val popup = super.createAndShowActionGroupPopup(actionGroup, event)
          popup.addListener(object : JBPopupListener {
            override fun onClosed(event: LightweightWindowEvent) {
              ApplicationManager.getApplication().invokeLater {
                myMorePopupVisible = false
                isVisible = myHoverState
              }
            }
          })
          return popup
        }
      }
      button.border = JBUI.Borders.emptyRight(5)
      button.isVisible = false
      myMoreButton = button

      val panel = JPanel(BorderLayout())
      panel.isOpaque = false
      panel.add(button, BorderLayout.NORTH)
      add(panel, BorderLayout.EAST)
      setComponentZOrder(panel, 0)
    }
    else {
      val timeComponent = JBLabel(DateFormatUtil.formatPrettyDateTime(notification.timestamp))
      timeComponent.putClientProperty(TIME_KEY, notification.timestamp)
      timeComponent.verticalAlignment = SwingConstants.TOP
      timeComponent.verticalTextPosition = SwingConstants.TOP
      timeComponent.toolTipText = DateFormatUtil.formatDateTime(notification.timestamp)
      timeComponent.border = JBUI.Borders.emptyRight(10)
      timeComponent.smallFontFunction()
      timeComponent.foreground = INFO_COLOR

      timeComponents.add(timeComponent)
      titlePanel!!.add(timeComponent, BorderLayout.EAST)

      myMoreButton = null
    }
  }

  private fun createAction(action: AnAction): Action {
    return object : AbstractAction(action.templateText) {
      override fun actionPerformed(e: ActionEvent) {
        runAction(action, e.source)
      }
    }
  }

  private fun runAction(action: AnAction, component: Any) {
    setNew(false)
    NotificationCollector.getInstance()
      .logNotificationActionInvoked(null, notification, action, NotificationCollector.NotificationPlace.ACTION_CENTER)
    Notification.fire(notification, action, DataManager.getInstance().getDataContext(component as Component))
  }

  fun removeFromParent() {
    for (component in UIUtil.findComponentsOfType(this, JTextComponent::class.java)) {
      singleSelectionHandler.remove(component)
    }
  }

  private fun createTextComponent(text: @Nls String): JEditorPane {
    val component = JEditorPane()
    component.isEditable = false
    component.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, java.lang.Boolean.TRUE)
    component.contentType = "text/html"
    component.isOpaque = false
    component.border = null

    val kit = HTMLEditorKitBuilder().withWordWrapViewFactory().build()
    NotificationsUtil.setLinkForeground(kit.styleSheet)
    component.editorKit = kit

    val listener = NotificationsUtil.wrapListener(notification)
    if (listener != null) {
      component.addHyperlinkListener(listener)
    }

    component.putClientProperty(AccessibleContext.ACCESSIBLE_NAME_PROPERTY, StringUtil.unescapeXmlEntities(StringUtil.stripHtml(text, " ")))

    component.text = text

    component.isEditable = false
    if (component.caret != null) {
      component.caretPosition = 0
    }

    myLafUpdater = Runnable {
      val newKit = HTMLEditorKitBuilder().withWordWrapViewFactory().build()
      NotificationsUtil.setLinkForeground(newKit.styleSheet)
      component.editorKit = newKit
      component.text = text
      component.revalidate()
      component.repaint()
    }

    return component
  }

  fun updateLaf() {
    myLafUpdater?.run()
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
        myMoreButton.isVisible = state
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
      setColor(NEW_COLOR)
    }
    else {
      setColor(BG_COLOR)
    }
  }

  private fun setColor(color: Color) {
    myRoundColor = color
    for (component in myBgComponents) {
      component.background = color
    }
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
    if (notification.title.contains(query, true)) {
      return true
    }
    val subtitle = notification.subtitle
    if (subtitle != null && subtitle.contains(query, true)) {
      return true
    }
    if (notification.content.contains(query, true)) {
      return true
    }
    for (action in notification.actions) {
      val text = action.templateText
      if (text != null && text.contains(query, true)) {
        return true
      }
    }
    return false
  }
}

private class MyDropDownAction(notificationComponent: NotificationComponent) : NotificationsManagerImpl.DropDownAction(null, null) {
  var collapseActionsDirection: Notification.CollapseActionsDirection = notificationComponent.notification.collapseDirection

  init {
    setListener(LinkListener { link, _ ->
      val group = DefaultActionGroup()
      val layout = link.parent.layout as DropDownActionLayout

      for (action in layout.actions) {
        if (!action.isVisible) {
          val notificationAction = action.linkData
          val popupAction = object : DumbAwareAction() {
            override fun update(e: AnActionEvent) {
              notificationAction.update(e)
            }

            override fun actionPerformed(e: AnActionEvent) {
              notificationComponent.setNew(false)
              notificationAction.actionPerformed(e)
            }
          }
          popupAction.copyFrom(notificationAction)
          group.add(popupAction)
        }
      }

      NotificationsManagerImpl.showPopup(link, group)
    }, null)

    text = notificationComponent.notification.dropDownText
    isVisible = false

    Notification.setDataProvider(notificationComponent.notification, this)
  }
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
    else {
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
    synchronized(myLock) {
      if (project == null) {
        if (myProjectToModel.isEmpty()) {
          myNotifications.add(notification)
        }
        else {
          for ((_project, model) in myProjectToModel.entries) {
            model.addNotification(_project, notification, myNotifications)
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
        model.addNotification(project, notification, myNotifications)
      }
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

  fun expire(notification: Notification) {
    synchronized(myLock) {
      myNotifications.remove(notification)
      for (model in myProjectToModel.values) {
        model.expire(notification)
      }
    }
  }

  fun expireAll() {
    val notifications = ArrayList<Notification>()

    synchronized(myLock) {
      notifications.addAll(myNotifications)
      myNotifications.clear()
      for (model in myProjectToModel.values) {
        model.expireAll(notifications)
      }
    }

    for (notification in notifications) {
      notification.expire()
    }
  }
}

private class ProjectNotificationModel {
  private val myNotifications = ArrayList<Notification>()
  private var myContent: NotificationContent? = null

  fun registerAndGetInitNotifications(content: NotificationContent, notifications: MutableList<Notification>) {
    notifications.addAll(myNotifications)
    myNotifications.clear()
    myContent = content
  }

  fun addNotification(project: Project, notification: Notification, appNotifications: List<Notification>) {
    if (myContent == null) {
      myNotifications.add(notification)

      EventLog.getLogModel(project).setStatusMessage(notification)

      val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(NotificationsToolWindowFactory.ID)
      if (toolWindow != null) {
        val notifications = ArrayList(appNotifications)
        notifications.addAll(myNotifications)
        UIUtil.invokeLaterIfNeeded { toolWindow.setIcon(IdeNotificationArea.getActionCenterNotificationIcon(notifications)) }
      }
    }
    else {
      UIUtil.invokeLaterIfNeeded { myContent!!.add(notification) }
    }
  }

  fun getStateNotifications(): List<Notification> {
    if (myContent == null) {
      return emptyList()
    }
    return myContent!!.getStateNotifications()
  }

  fun getNotifications(appNotifications: List<Notification>): List<Notification> {
    if (myContent == null) {
      val notifications = ArrayList(appNotifications)
      notifications.addAll(myNotifications)
      return notifications
    }
    return myContent!!.getNotifications()
  }

  fun expire(notification: Notification) {
    myNotifications.remove(notification)
    if (myContent != null) {
      UIUtil.invokeLaterIfNeeded { myContent!!.expire(notification) }
    }
  }

  fun expireAll(notifications: MutableList<Notification>) {
    notifications.addAll(myNotifications)
    myNotifications.clear()
    if (myContent != null) {
      UIUtil.invokeLaterIfNeeded { myContent!!.expire(null) }
    }
  }
}