package com.intellij.driver.sdk

import com.intellij.driver.client.Driver
import com.intellij.driver.client.Remote
import com.intellij.driver.client.service
import com.intellij.driver.model.OnDispatcher
import com.intellij.driver.model.RdTarget
import com.intellij.driver.sdk.ui.remote.Component
import com.intellij.openapi.diagnostic.fileLogger
import java.awt.event.InputEvent

@Remote(value = "com.intellij.openapi.actionSystem.ActionManager")
interface ActionManager {
  fun getAction(actionId: String): AnAction?

  fun tryToExecute(action: AnAction,
                   inputEvent: InputEvent?,
                   contextComponent: Component?,
                   place: String?,
                   now: Boolean): ActionCallback
}

@Remote(value = "com.intellij.openapi.actionSystem.AnAction")
interface AnAction

@Remote(value = "com.intellij.openapi.util.ActionCallback")
interface ActionCallback {
  fun isRejected(): Boolean
  fun isProcessed(): Boolean
  fun getError(): String
}

fun Driver.invokeAction(actionId: String, now: Boolean = true, component: Component? = null, rdTarget: RdTarget? = null): ActionCallback {
  fileLogger().info("Invoke action $actionId")
  return withContext(OnDispatcher.EDT) {
    val target = rdTarget ?: if (isRemoteIdeMode) RdTarget.FRONTEND else RdTarget.DEFAULT
    val actionManager = service<ActionManager>(target)
    val action = actionManager.getAction(actionId)
    if (action == null) {
      throw IllegalStateException("Action $actionId was not found")
    }
    else {
      val actionCallback = actionManager.tryToExecute(action, null, component, null, now)
      if (now || actionCallback.isProcessed()) {
        if (actionCallback.isRejected()) {
          fileLogger().info("Action $actionId was rejected with error: ${actionCallback.getError()}")
        }
        else {
          fileLogger().info("Action $actionId was executed")
        }
      }
      return@withContext actionCallback
    }
  }
}
