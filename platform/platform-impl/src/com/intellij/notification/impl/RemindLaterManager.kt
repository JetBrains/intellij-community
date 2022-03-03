// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.notification.impl

import com.intellij.ide.DataManager
import com.intellij.notification.Notification
import com.intellij.notification.NotificationRemindLaterHandler
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.SimpleModificationTracker
import com.intellij.openapi.wm.WindowManager
import com.intellij.util.concurrency.AppExecutorUtil
import org.jdom.Element
import java.util.concurrent.TimeUnit

/**
 * @author Alexander Lobas
 */
@State(name = "NotificationRemindLaterService",
       storages = [Storage(value = "notification.remind.later.xml", roamingType = RoamingType.DISABLED)])
@Service(Service.Level.APP)
class RemindLaterManager : SimpleModificationTracker(), PersistentStateComponent<Element> {
  companion object {
    @JvmStatic
    fun createAction(notification: Notification, delay: Long): Runnable? {
      val handlerId = notification.remindLaterHandlerId

      if (handlerId == null) {
        if (notification.listener != null || notification.actions.isNotEmpty() || notification.contextHelpAction != null) {
          return null
        }

        return Runnable {
          instance().addSimpleNotification(notification, delay)
        }
      }

      val handler = NotificationRemindLaterHandler.findHandler(handlerId)
      if (handler == null) {
        return null
      }

      return Runnable {
        instance().addNotificationWithActions(handler, notification, delay)
      }
    }

    @JvmStatic
    fun instance(): RemindLaterManager = ApplicationManager.getApplication().getService(RemindLaterManager::class.java)
  }

  private val myRootElement = Element("state")

  private fun addSimpleNotification(notification: Notification, delay: Long, action: ((Element) -> Unit)? = null) {
    val element = createElement(notification, System.currentTimeMillis() + delay)
    action?.invoke(element)

    myRootElement.addContent(element)
    incModificationCount()

    schedule(element, delay)
  }

  private fun addNotificationWithActions(handler: NotificationRemindLaterHandler, notification: Notification, delay: Long) {
    addSimpleNotification(notification, delay) {
      val element = Element("Actions")
      element.setAttribute("id", handler.getID())
      element.addContent(handler.getActionsState(notification))
      it.addContent(element)
    }
  }

  private fun createElement(notification: Notification, time: Long): Element {
    val element = Element("Notification")

    element.setAttribute("time", time.toString())

    element.setAttribute("type", notification.type.name)
    element.setAttribute("groupId", notification.groupId)

    element.setAttribute("suggestion", notification.isSuggestionType.toString())
    element.setAttribute("importantSuggestion", notification.isImportantSuggestion.toString())

    val displayId = notification.displayId
    if (displayId != null) {
      element.setAttribute("displayId", displayId)
    }

    if (notification.hasTitle()) {
      val title = Element("Title")
      title.text = notification.title
      element.addContent(title)

      val subtitle = notification.subtitle
      if (subtitle != null) {
        val subtitleElement = Element("SubTitle")
        subtitleElement.text = subtitle
        element.addContent(subtitleElement)
      }
    }
    if (notification.hasContent()) {
      val content = Element("Content")
      content.text = notification.content
      element.addContent(content)
    }

    return element
  }

  private fun schedule(element: Element, delay: Long) {
    AppExecutorUtil.getAppScheduledExecutorService().schedule({ execute(element) }, delay, TimeUnit.MILLISECONDS)
  }

  private fun execute(element: Element) {
    myRootElement.removeContent(element)
    incModificationCount()

    val groupId = element.getAttributeValue("groupId")
    if (groupId == null) {
      return
    }

    val type = NotificationType.valueOf(element.getAttributeValue("type") ?: "INFORMATION")

    @NlsSafe val title = element.getChildText("Title") ?: ""
    @NlsSafe val content = element.getChildText("Content") ?: ""
    @NlsSafe val subtitle = element.getChildText("SubTitle")

    val notification = Notification(groupId, title, content, type)
    notification.subtitle = subtitle

    val displayId = element.getAttributeValue("displayId")
    if (displayId != null) {
      notification.setDisplayId(displayId)
    }

    val suggestion = element.getAttributeValue("suggestion")
    if (suggestion != null) {
      notification.isSuggestionType = suggestion.toBoolean()
    }

    val importantSuggestion = element.getAttributeValue("importantSuggestion")
    if (importantSuggestion != null) {
      notification.isImportantSuggestion = importantSuggestion.toBoolean()
    }

    val actionsChild = element.getChild("Actions")
    if (actionsChild != null) {
      val handlerId = actionsChild.getAttributeValue("id")
      if (handlerId == null) {
        return
      }

      val handler = NotificationRemindLaterHandler.findHandler(handlerId)
      if (handler == null) {
        return
      }

      val children = actionsChild.children
      if (children.size != 1) {
        return
      }

      notification.setRemindLaterHandlerId(handlerId)

      if (!handler.setActions(notification, children[0])) {
        return
      }
    }

    ApplicationManager.getApplication().invokeLater {
      notification.notify(
        CommonDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext(WindowManager.getInstance().mostRecentFocusedWindow)))
    }
  }

  override fun getState(): Element {
    return myRootElement
  }

  override fun initializeComponent() {
    val time = System.currentTimeMillis()
    val removeList = ArrayList<Element>()

    for (element in myRootElement.getChildren("Notification")) {
      val timeValue = element.getAttributeValue("time")
      if (timeValue == null) {
        removeList.add(element)
        continue
      }

      val nextTime: Long
      try {
        nextTime = timeValue.toLong()
      }
      catch (_: Exception) {
        removeList.add(element)
        continue
      }

      val delay = nextTime - time
      if (delay > 0) {
        schedule(element, delay)
      }
      else {
        execute(element)
      }
    }

    for (element in removeList) {
      myRootElement.removeContent(element)
    }
    if (removeList.isNotEmpty()) {
      incModificationCount()
    }
  }

  override fun loadState(state: Element) {
    myRootElement.removeContent()

    for (element in state.getChildren("Notification")) {
      myRootElement.addContent(element.clone())
    }
  }
}

class RemindLaterActivity : StartupActivity {
  override fun runActivity(project: Project) {
    RemindLaterManager.instance()
  }
}