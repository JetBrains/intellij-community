// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.notification.impl

import com.intellij.icons.AllIcons
import com.intellij.ide.DataManager
import com.intellij.ide.IdeBundle
import com.intellij.ide.impl.ProjectUtilCore
import com.intellij.ide.util.PropertiesComponent
import com.intellij.notification.Notification
import com.intellij.notification.impl.ui.NotificationsUtil
import com.intellij.notification.impl.widget.IdeNotificationArea
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Divider
import com.intellij.openapi.ui.NullableComponent
import com.intellij.openapi.ui.OnePixelDivider
import com.intellij.openapi.ui.ex.MultiLineLabel
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.WindowManager
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import com.intellij.ui.*
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBOptionButton
import com.intellij.ui.components.JBPanelWithEmptyText
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.labels.LinkLabel
import com.intellij.ui.components.panels.HorizontalLayout
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.Alarm
import com.intellij.util.ModalityUiUtil
import com.intellij.util.text.DateFormatUtil
import com.intellij.util.ui.GraphicsUtil
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.Nls
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Graphics
import java.awt.event.*
import java.util.function.Consumer
import javax.accessibility.AccessibleContext
import javax.swing.*

/**
 * @author Alexander Lobas
 */

class NotificationsToolWindowFactory : ToolWindowFactory, DumbAware {
  companion object {
    const val ID = "Notifications"

    val myNotificationList = ArrayList<Notification>()
    val myToolWindowList = ArrayList<ToolWindow>()
    val myLock = Object()
    val ADD_KEY = Key.create<Consumer<Notification>>("callbackInterface")
    val REMOVE_KEY = Key.create<Consumer<Notification?>>("removeCallbackInterface")

    fun remove(notification: Notification?) {
      iterate { it.accept(notification) }

      synchronized(myLock) {
        if (notification == null) {
          myNotificationList.clear()
        }
        else {
          myNotificationList.remove(notification)
        }
      }
    }

    fun iterate(callback: (Consumer<Notification?>) -> Unit) {
      for (project in ProjectUtilCore.getOpenProjects()) {
        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(ID)
        if (toolWindow != null) {
          callback.invoke(toolWindow.contentManager.getContent(0)!!.getUserData(REMOVE_KEY)!!)
        }
      }
    }
  }

  override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
    if (!Registry.`is`("ide.notification.action.center", false)) {
      toolWindow.isShowStripeButton = false
      toolWindow.remove()
      return
    }

    val panel = JBPanelWithEmptyText(BorderLayout())
    panel.background = NotificationComponent.BG_COLOR

    panel.emptyText.appendLine(AllIcons.Ide.Notification.NoEvents, "", SimpleTextAttributes.REGULAR_ATTRIBUTES, null)
    panel.emptyText.appendLine("")
    panel.emptyText.appendLine(IdeBundle.message("notifications.toolwindow.empty.text.first.line"))
    @Suppress("DialogTitleCapitalization")
    panel.emptyText.appendLine(IdeBundle.message("notifications.toolwindow.empty.text.second.line"))

    val suggestions = NotificationGroupComponent(panel, true, project)

    val timeline = NotificationGroupComponent(panel, false, project)

    val splitter = object : OnePixelSplitter(true, .5f) {
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
    splitter.firstComponent = suggestions
    splitter.secondComponent = timeline
    panel.add(splitter)

    val myNotifications = ArrayList<Notification>()

    val content = ContentFactory.SERVICE.getInstance().createContent(panel, "", false)
    val consumer = Consumer<Notification> { notification ->
      if (notification.isSuggestionType) {
        suggestions.add(notification)
      }
      else {
        timeline.add(notification)
      }
      myNotifications.add(notification)
      updateIcon(toolWindow, myNotifications)
    }
    content.putUserData(ADD_KEY, consumer)

    content.putUserData(REMOVE_KEY, Consumer<Notification?> { notification ->
      if (notification == null) {
        myNotifications.clear()
        suggestions.clear()
        timeline.clear()
      }
      else {
        if (notification.isSuggestionType) {
          suggestions.remove(notification)
        }
        else {
          timeline.remove(notification)
        }
        myNotifications.remove(notification)
      }
      updateIcon(toolWindow, myNotifications)
    })

    timeline.setClearCallback { notifications: List<Notification> ->
      myNotifications.removeAll(notifications)
      updateIcon(toolWindow, myNotifications)
    }

    synchronized(myLock) {
      myToolWindowList.add(toolWindow)
      Disposer.register(toolWindow.disposable, Disposable {
        synchronized(myLock) {
          myToolWindowList.remove(toolWindow)
        }
      })

      for (notification in myNotificationList) {
        consumer.accept(notification)
      }
      myNotificationList.clear()
    }

    val contentManager = toolWindow.contentManager
    contentManager.addContent(content)
    contentManager.setSelectedContent(content)

    project.messageBus.connect(toolWindow.disposable).subscribe(ToolWindowManagerListener.TOPIC, object : ToolWindowManagerListener {
      private var myVisible = true

      override fun stateChanged(toolWindowManager: ToolWindowManager) {
        val visible = toolWindow.isVisible
        if (myVisible != visible) {
          myVisible = visible
          if (visible) {
            val ideFrame = WindowManager.getInstance().getIdeFrame(project)
            val balloonLayout = ideFrame!!.balloonLayout as BalloonLayoutImpl
            balloonLayout.closeAll()
          }
        }
        if (!visible) {
          suggestions.clearNewState()
          timeline.clearNewState()
        }
      }
    })
  }

  private fun updateIcon(toolWindow: ToolWindow, notifications: List<Notification>) {
    toolWindow.setIcon(IdeNotificationArea.getActionCenterNotificationIcon(notifications))
  }
}

private class NotificationGroupComponent(private val myMainPanel: JPanel,
                                         private val mySuggestionType: Boolean,
                                         private val myProject: Project) :
  JPanel(BorderLayout()), NullableComponent {

  private val myTitle = JBLabel(
    IdeBundle.message(if (mySuggestionType) "notifications.toolwindow.suggestions" else "notifications.toolwindow.timeline"))

  private val myList = JPanel(VerticalLayout(JBUI.scale(8)))
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

  private var myMouseInside = false
  private val myClearAllVisibleCallback: (() -> Unit)?

  private val myEventHandler = ComponentEventHandler(this)

  private val myTimeComponents = ArrayList<JLabel>()
  private val myTimeAlarm = Alarm(myProject)

  private val mySuggestionGotItPanel = JPanel(BorderLayout())

  private lateinit var myClearCallback: (List<Notification>) -> Unit

  init {
    background = NotificationComponent.BG_COLOR

    val mainPanel = JPanel(BorderLayout(0, JBUI.scale(8)))
    mainPanel.isOpaque = false
    mainPanel.border = JBUI.Borders.empty(8, 8, 8, 0)
    add(mainPanel)

    myTitle.font = JBFont.medium()
    myTitle.foreground = NotificationComponent.INFO_COLOR

    if (mySuggestionType) {
      mySuggestionGotItPanel.background = JBColor(0xE7EEF6, 0xE7EEF6)
      mySuggestionGotItPanel.isVisible = false
      mySuggestionGotItPanel.border = JBUI.Borders.customLineBottom(JBColor.border())
      add(mySuggestionGotItPanel, BorderLayout.NORTH)

      val gotItTitle = MultiLineLabel(IdeBundle.message("notifications.toolwindow.suggestion.gotit.title"))
      gotItTitle.font = JBFont.medium()
      gotItTitle.border = JBUI.Borders.empty(7, 12, 7, 0)
      mySuggestionGotItPanel.add(gotItTitle, BorderLayout.WEST)

      val panel = JPanel(BorderLayout())
      panel.isOpaque = false
      panel.border = JBUI.Borders.empty(7, 0, 0, 12)
      mySuggestionGotItPanel.add(panel, BorderLayout.EAST)
      panel.add(LinkLabel<Any>(IdeBundle.message("notifications.toolwindow.suggestion.gotit.link"), null) { _, _ ->
        mySuggestionGotItPanel.isVisible = false
        myTitle.isVisible = true
        fullRepaint()
      }, BorderLayout.NORTH)

      myTitle.border = JBUI.Borders.emptyLeft(10)
      mainPanel.add(myTitle, BorderLayout.NORTH)

      myClearAllVisibleCallback = null
    }
    else {
      val panel = JPanel(BorderLayout())
      panel.isOpaque = false
      panel.border = JBUI.Borders.emptyLeft(10)

      panel.add(myTitle, BorderLayout.WEST)

      val clearAll = LinkLabel(IdeBundle.message("notifications.toolwindow.timeline.clear.all"), null) { _: LinkLabel<Unit>, _: Unit? ->
        clearAll()
      }
      clearAll.font = JBFont.medium()
      clearAll.border = JBUI.Borders.emptyRight(20)
      clearAll.isVisible = false
      panel.add(clearAll, BorderLayout.EAST)

      myClearAllVisibleCallback = {
        clearAll.isVisible = myMouseInside && myList.componentCount > 0
      }
      myEventHandler.mouseEnter {
        if (!myMouseInside) {
          myMouseInside = true
          myClearAllVisibleCallback.invoke()
        }
      }
      myEventHandler.mouseExit {
        if (myMouseInside) {
          myMouseInside = false
          myClearAllVisibleCallback.invoke()
        }
      }

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

  override fun paintComponent(g: Graphics) {
    super.paintComponent(g)
    if (myScrollValue > 0) {
      g.color = JBColor.border()
      val y = myScrollPane.y - 1
      g.drawLine(0, y, width, y)
    }
  }

  fun add(notification: Notification) {
    val component = NotificationComponent(notification, myTimeComponents)
    component.setNew(true)

    myList.add(component)
    myEventHandler.add(component)

    if (mySuggestionType && !PropertiesComponent.getInstance().getBoolean("notification.suggestion.dont.show.gotit")) {
      PropertiesComponent.getInstance().setValue("notification.suggestion.dont.show.gotit", true)
      mySuggestionGotItPanel.isVisible = true
      myTitle.isVisible = false
    }

    updateContent()
  }

  fun remove(notification: Notification) {
    val count = myList.componentCount
    for (i in 0 until count) {
      val component = myList.getComponent(i) as NotificationComponent
      if (component.notification === notification) {
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
    val count = myList.componentCount
    for (i in 0 until count) {
      val component = myList.getComponent(i) as NotificationComponent
      notifications.add(component.notification)
    }
    clear()
    myClearCallback.invoke(notifications)
  }

  fun clear() {
    myList.removeAll()
    updateContent()
  }

  fun clearNewState() {
    val count = myList.componentCount
    for (i in 0 until count) {
      val component = myList.getComponent(i) as NotificationComponent
      component.setNew(false)
    }
  }

  private fun updateContent() {
    myClearAllVisibleCallback?.invoke()

    if (!mySuggestionType) {
      myTimeAlarm.cancelAllRequests()

      object : Runnable {
        override fun run() {
          for (timeComponent in myTimeComponents) {
            timeComponent.text = DateFormatUtil.formatPrettyDateTime(
              timeComponent.getClientProperty(NotificationComponent.TIME_KEY) as Long)
          }

          if (myTimeComponents.isNotEmpty()) {
            myTimeAlarm.addRequest(this, 30000)
          }
        }
      }.run()
    }

    fullRepaint()
  }

  private fun fullRepaint() {
    myMainPanel.doLayout()
    myMainPanel.revalidate()
    myMainPanel.repaint()
  }

  override fun isVisible(): Boolean {
    return super.isVisible() && myList.componentCount > 0
  }

  override fun isNull(): Boolean = !isVisible
}

private class NotificationComponent(val notification: Notification, timeComponents: ArrayList<JLabel>) :
  JPanel(BorderLayout(JBUI.scale(7), 0)) {

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
  private var myRoundColor = BG_COLOR

  init {
    isOpaque = true
    background = BG_COLOR
    border = JBUI.Borders.empty(5, 10, 5, 0)

    val iconPanel = JPanel(BorderLayout())
    iconPanel.isOpaque = false
    iconPanel.add(JBLabel(NotificationsUtil.getIcon(notification)), BorderLayout.NORTH)
    add(iconPanel, BorderLayout.WEST)

    val centerPanel = JPanel(VerticalLayout(JBUI.scale(8)))
    centerPanel.isOpaque = false

    if (notification.hasTitle()) {
      val titleContent = NotificationsUtil.buildHtml(notification, "white-space:nowrap;", false, null, null)
      centerPanel.add(JBLabel(titleContent))
    }

    if (notification.hasContent()) {
      val textContent = NotificationsUtil.buildHtml(notification, null, true, null, null)
      centerPanel.add(createTextComponent(textContent))
    }

    val actions = notification.actions
    val actionsSize = actions.size

    if (actionsSize > 0) {
      val actionPanel = JPanel(HorizontalLayout(JBUIScale.scale(16)))
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
        for (action in actions) {
          @Suppress("DialogTitleCapitalization")
          actionPanel.add(LinkLabel<Any>(action.templateText, action.templatePresentation.icon) { link, _ -> runAction(action, link) })
        }
      }
      centerPanel.add(actionPanel)
    }

    add(centerPanel)

    val panel = JPanel(BorderLayout())
    panel.isOpaque = false

    if (notification.isSuggestionType) {
      val group = DefaultActionGroup()
      group.isPopup = true
      group.add(object : AnAction(IdeBundle.message("notifications.toolwindow.remind.tomorrow")) {
        override fun actionPerformed(e: AnActionEvent) {
          TODO("Not yet implemented")
        }
      })
      group.add(object : AnAction(IdeBundle.message("notifications.toolwindow.dont.show.again.for.this.project")) {
        override fun actionPerformed(e: AnActionEvent) {
          TODO("Not yet implemented")
        }
      })
      group.add(object : AnAction(IdeBundle.message("notifications.toolwindow.dont.show.again")) {
        override fun actionPerformed(e: AnActionEvent) {
          TODO("Not yet implemented")
        }
      })

      val presentation = Presentation()
      presentation.icon = AllIcons.Actions.More
      presentation.putClientProperty(ActionButton.HIDE_DROPDOWN_ICON, java.lang.Boolean.TRUE)

      val button = ActionButton(group, presentation, ActionPlaces.UNKNOWN, ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE)
      button.border = JBUI.Borders.emptyRight(5)
      button.isVisible = false
      myMoreButton = button
      panel.add(button, BorderLayout.NORTH)
    }
    else {
      val timeComponent = JBLabel(DateFormatUtil.formatPrettyDateTime(notification.timestamp))
      timeComponent.putClientProperty(TIME_KEY, notification.timestamp)
      timeComponent.toolTipText = DateFormatUtil.formatDateTime(notification.timestamp)
      timeComponent.border = JBUI.Borders.emptyRight(10)
      timeComponent.font = JBFont.medium()
      timeComponent.foreground = INFO_COLOR

      timeComponents.add(timeComponent)
      panel.add(timeComponent, BorderLayout.NORTH)

      myMoreButton = null
    }

    add(panel, BorderLayout.EAST)
  }

  private fun createAction(action: AnAction): Action {
    return object : AbstractAction(action.templateText) {
      override fun actionPerformed(e: ActionEvent) {
        runAction(action, e.source)
      }
    }
  }

  private fun runAction(action: AnAction, component: Any) {
    NotificationCollector.getInstance()
      .logNotificationActionInvoked(null, notification, action, NotificationCollector.NotificationPlace.ACTION_CENTER)
    Notification.fire(notification, action, DataManager.getInstance().getDataContext(component as Component))
  }

  private fun createTextComponent(text: @Nls String): JTextPane {
    val component = JTextPane()
    component.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, java.lang.Boolean.TRUE)
    component.contentType = "text/html"
    component.isOpaque = false
    component.isFocusable = false
    component.border = null

    val kit = UIUtil.JBWordWrapHtmlEditorKit()
    kit.styleSheet.addRule("a {color: " + ColorUtil.toHtmlColor(JBUI.CurrentTheme.Link.Foreground.ENABLED) + "}")
    component.editorKit = kit
    component.addHyperlinkListener(BrowserHyperlinkListener.INSTANCE)

    component.putClientProperty(AccessibleContext.ACCESSIBLE_NAME_PROPERTY, StringUtil.unescapeXmlEntities(StringUtil.stripHtml(text, " ")))

    component.text = text

    component.isEditable = false
    if (component.caret != null) {
      component.caretPosition = 0
    }

    return component
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
    myMoreButton?.isVisible = state
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
      g.fillRoundRect(0, 0, width, height, 12, 12)
      config.restore()
    }
  }
}

private class ComponentEventHandler(private val myTarget: NotificationGroupComponent) {
  private var myHoverComponent: NotificationComponent? = null
  private var myEnterCallback: (() -> Unit)? = null
  private var myExitCallback: (() -> Unit)? = null

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

    override fun mouseEntered(e: MouseEvent) {
      if (myEnterCallback != null && myTarget === findTarget(e)) {
        myEnterCallback!!.invoke()
      }
    }

    override fun mouseExited(e: MouseEvent) {
      if (myExitCallback != null && myTarget === findTarget(e)) {
        myExitCallback!!.invoke()
      }
      if (myHoverComponent != null) {
        val component = myHoverComponent!!
        if (component.isHover()) {
          component.setHover(false)
        }
        myHoverComponent = null
      }
    }
  }

  private fun findTarget(e: MouseEvent): NotificationGroupComponent? {
    return ComponentUtil.getParentOfType(NotificationGroupComponent::class.java, e.component)
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

  fun mouseEnter(callback: () -> Unit) {
    myEnterCallback = callback
  }

  fun mouseExit(callback: () -> Unit) {
    myExitCallback = callback
  }
}

fun addNotification(project: Project?, notification: Notification) {
  if (project == null) {
    addToCache(notification)
  }
  else {
    val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(NotificationsToolWindowFactory.ID)
    if (toolWindow == null) {
      addToCache(notification)
    }
    else {
      ModalityUiUtil.invokeLaterIfNeeded(ModalityState.NON_MODAL, project.disposed, Runnable {
        send(toolWindow, notification)
      })
    }
  }
}

private fun addToCache(notification: Notification) {
  val toolWindows = ArrayList<ToolWindow>()

  synchronized(NotificationsToolWindowFactory.myLock) {
    toolWindows.addAll(NotificationsToolWindowFactory.myToolWindowList)
  }

  if (toolWindows.isNotEmpty()) {
    ModalityUiUtil.invokeLaterIfNeeded(ModalityState.NON_MODAL, Runnable {
      for (toolWindow in toolWindows) {
        send(toolWindow, notification)
      }
    })
    return
  }

  synchronized(NotificationsToolWindowFactory.myLock) {
    NotificationsToolWindowFactory.myNotificationList.add(notification)
  }
}

private fun send(toolWindow: ToolWindow, notification: Notification) {
  toolWindow.contentManager.getContent(0)!!.getUserData(NotificationsToolWindowFactory.ADD_KEY)!!.accept(notification)
}