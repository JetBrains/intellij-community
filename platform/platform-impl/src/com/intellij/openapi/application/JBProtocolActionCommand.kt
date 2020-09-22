// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.application

import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.diagnostic.logger

class JBProtocolActionCommand : JBProtocolCommand(COMMAND) {
  private companion object {
    val LOG = logger<JBProtocolActionCommand>()
    const val COMMAND = "action"
    const val EXECUTE = "execute"
    const val PROJECT = "project"
    const val ID = "id"
  }

  override fun perform(target: String, parameters: MutableMap<String, String>) {
    if (target != EXECUTE) {
      LOG.warn("Unsupported target: $target. Supported: '$EXECUTE'")
      return
    }
    val projectName = parameters[PROJECT]
    if (projectName.isNullOrEmpty()) {
      LOG.warn("Unable to execute action: '$PROJECT' parameter is missing")
      return
    }
    val actionId = parameters[ID]
    if (actionId.isNullOrEmpty()) {
      LOG.warn("Unable to execute action: '$ID' parameter is missing")
      return
    }
    openProjectAndExecute(projectName) { project ->
      val action = ActionManager.getInstance().getAction(actionId)
      if (action == null) {
        Notification(
          JBProtocolCommand::class.java.simpleName, "JetBrains Protocol action execution failed",
          "Unable to find action by id $actionId",
          NotificationType.ERROR
        ).notify(project)
      }
      else {
        val context = DataContext {
          when (it) {
            CommonDataKeys.PROJECT.name -> project
            else -> parameters[it]
          }
        }
        val event = AnActionEvent.createFromAnAction(action, null,
                                                     ActionPlaces.ACTION_SEARCH,
                                                     context)
        ActionUtil.performActionDumbAware(action, event)
      }
    }
  }
}