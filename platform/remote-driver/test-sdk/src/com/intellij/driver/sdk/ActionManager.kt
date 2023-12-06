package com.intellij.driver.sdk

import com.intellij.driver.client.Driver
import com.intellij.driver.client.Remote
import com.intellij.driver.client.utility
import com.intellij.driver.sdk.ui.remote.Component
import java.awt.event.InputEvent


fun Driver.invokeAction(actionId: String, now: Boolean = true) {
  val actionManager = utility<ActionManager>().getInstance()
  val action = actionManager.getAction(actionId)
  withWriteAction {
    actionManager.tryToExecute(action, null, null, null, now)
  }
}

@Remote(value = "com.intellij.openapi.actionSystem.ActionManager")
interface ActionManager {
  fun getInstance(): ActionManager
  fun getAction(actionId: String): AnAction
  fun tryToExecute(action: AnAction,
                   inputEvent: InputEvent?,
                   contextComponent: Component?,
                   place: String?,
                   now: Boolean): ActionCallback
}

@Remote(value = "com.intellij.openapi.actionSystem.AnAction")
interface AnAction
@Remote(value = "com.intellij.openapi.util.ActionCallback")
interface ActionCallback
