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

  fun tryToExecute(
    action: AnAction,
    inputEvent: InputEvent?,
    contextComponent: Component?,
    place: String?,
    now: Boolean,
  ): ActionCallback
}

@Remote("com.intellij.openapi.actionSystem.ex.ActionUtil")
interface ActionUtils {
  fun getActions(component: Component): List<AnAction>
}

@Remote(value = "com.intellij.openapi.actionSystem.AnAction")
interface AnAction {
  fun getShortcutSet(): ShortcutSet
  fun getTemplateText(): String
}

@Remote("com.intellij.openapi.actionSystem.ShortcutSet")
interface ShortcutSet {
  fun getShortcuts(): Array<Shortcut>
}

@Remote("com.intellij.openapi.actionSystem.Shortcut")
interface Shortcut

@Remote(value = "com.intellij.openapi.util.ActionCallback")
interface ActionCallback {
  fun isRejected(): Boolean
  fun getError(): String
}

/**
 * The 'currently focused component' is poorly defined on the backend
 * So some fixed component needs to be passed into [ActionManager.tryToExecute]
 */
fun Driver.invokeGlobalBackendAction(actionId: String, project: Project? = null, now: Boolean = true) {
  val contextProject = project ?: service<ProjectManager>(rdTarget = RdTarget.BACKEND).getOpenProjects().single()
  val targetComponent = service<WindowManager>(rdTarget = RdTarget.BACKEND).getIdeFrame(contextProject)?.getComponent() // make sure there's an action in the context
  invokeAction(actionId, now, component = targetComponent, rdTarget = RdTarget.BACKEND)
}

fun Driver.invokeAction(actionId: String, now: Boolean = true, component: Component? = null, place: String? = null, rdTarget: RdTarget? = null) {
  val target = rdTarget ?: if (isRemoteIdeMode) RdTarget.FRONTEND else RdTarget.DEFAULT
  val actionManager = service<ActionManager>(target)
  val action = withContext(OnDispatcher.EDT) {
    actionManager.getAction(actionId)
  }
  checkNotNull(action) { "Action $actionId was not found" }
  fileLogger().info("Invoking action $actionId on $target")
  val actionCallback = step("Invoke action ${action.getTemplateText()}") {
    withContext(OnDispatcher.EDT) {
      actionManager.tryToExecute(action, null, component, place, now)
    }
  }
  check(!actionCallback.isRejected()) { "Action $actionId was rejected with error: ${actionCallback.getError()}" }
}
