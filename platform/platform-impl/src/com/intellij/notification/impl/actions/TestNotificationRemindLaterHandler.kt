// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.notification.impl.actions

import com.intellij.notification.Notification
import com.intellij.notification.NotificationRemindLaterHandler
import com.intellij.notification.NotificationRemindLaterHandlerWithState
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.ui.Messages
import com.intellij.util.xmlb.annotations.CollectionBean
import org.jdom.Element

/**
 * @author Alexander Lobas
 */
class TestNotificationRemindLaterHandler : NotificationRemindLaterHandler {
  override fun getID(): String {
    return "ZZZ_1"
  }

  override fun getActionsState(notification: Notification): Element {
    val element = Element("ActionNames")

    for ((index, action) in notification.actions.withIndex()) {
      element.setAttribute("action$index", action.templateText)
    }

    return element
  }

  override fun setActions(notification: Notification, element: Element): Boolean {
    var index = 0
    while (true) {
      val action = element.getAttributeValue("action$index")
      if (action == null) {
        return true
      }
      notification.addAction(object : AnAction(action) {
        override fun actionPerformed(e: AnActionEvent) {
          Messages.showInfoMessage("Run from Remind Later", "Action $action")
        }
      })
      index++
    }
  }
}

class ActionsState : BaseState() {
  @get:CollectionBean
  val actions by list<String>()
}

class TestNotificationRemindLaterHandler2 : NotificationRemindLaterHandlerWithState<ActionsState>(ActionsState::class.java) {
  override fun getID() = "ZZZ_2"

  override fun getState(notification: Notification): ActionsState {
    val state = ActionsState()
    for (action in notification.actions) {
      state.actions.add(action.templateText!!)
    }
    return state
  }

  override fun setState(notification: Notification, state: ActionsState): Boolean {
    for (action in state.actions) {
      notification.addAction(object : AnAction(action) {
        override fun actionPerformed(e: AnActionEvent) {
          Messages.showInfoMessage("Run2 from Remind Later", "Action $action")
        }
      })
    }
    return true
  }
}