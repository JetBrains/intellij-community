// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.updateSettings.impl

import com.intellij.diagnostic.rethrowControlFlowException
import com.intellij.ide.IdeBundle
import com.intellij.ide.plugins.PluginManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.ide.plugins.PluginPermissionJavaCaller
import com.intellij.ide.plugins.PluginPermissionJavaShim
import com.intellij.ide.plugins.PluginPermissionNotGrantedException
import com.intellij.ide.plugins.PluginPermissionRequest
import com.intellij.ide.plugins.PluginPermissionService
import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.util.ReflectionUtil
import com.intellij.util.xmlb.annotations.XCollection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext

private val LOG = logger<PluginPermissionServiceImpl>()

/**
 * Asks the user to allow a [PluginPermissionRequest] with a dialog.
 *
 * The dialog has the "Allow", "Allow Always" and "Deny" buttons.
 * The service stores each "Allow Always" decision for the requesting plugin and the [PluginPermissionHandler.getPermissionKey] key.
 * In a headless environment, the service allows only a request that has an "Allow Always" decision.
 * [ResetPluginPermissionsAction] removes all stored decisions.
 *
 * The service logs each request, the decision, and the result of the action at the info level.
 * Enable the debug level for this class to also log the stack trace of each request.
 */
@State(name = "PluginPermissions", storages = [Storage(StoragePathMacros.NON_ROAMABLE_FILE)])
internal class PluginPermissionServiceImpl(coroutineScope: CoroutineScope)
  : PluginPermissionService, SimplePersistentStateComponent<PluginPermissionState>(PluginPermissionState()) {

  private val handlers: Map<Class<*>, PluginPermissionHandler<*, *>> = listOf(
    InstallPluginPermissionHandler(coroutineScope),
    EnablePluginPermissionHandler(coroutineScope),
    DisablePluginPermissionHandler(coroutineScope),
    AccessPluginClassLoadersPermissionHandler(),
  ).associateBy<PluginPermissionHandler<*, *>, Class<*>> { it.requestClass }

  // guards PluginPermissionState.alwaysAllowed, because a request and ResetPluginPermissionsAction can change it at the same time
  private val decisionLock = Any()

  /** The number of stored "Allow Always" decisions. */
  val storedDecisionCount: Int
    get() = synchronized(decisionLock) { state.alwaysAllowed.size }

  /**
   * Removes all stored "Allow Always" decisions.
   * After this call, each permission request shows the dialog again.
   *
   * @return the number of removed decisions.
   */
  fun resetDecisions(): Int {
    val removed = synchronized(decisionLock) {
      val decisions = state.alwaysAllowed.toList()
      state.alwaysAllowed.clear()
      decisions
    }
    LOG.info("The user resets ${removed.size} stored plugin permission decisions: $removed")
    return removed.size
  }

  override suspend fun <T, R> withPermission(request: PluginPermissionRequest<T>, action: suspend (T) -> R): Result<R> {
    // the call stack contains the caller only before the first suspension point
    val requestTrace = Throwable("Plugin permission request")
    // depth 3 is the direct caller of this function. Call getCallerClass directly here, because a helper function changes the depth.
    val directCallerClass = ReflectionUtil.getCallerClass(3)
    val callerClass = if (directCallerClass != null && isJavaShimClass(directCallerClass)) {
      currentCoroutineContext()[PluginPermissionJavaCaller]?.callerClass
    }
    else {
      directCallerClass
    }

    val requester = callerClass?.let { PluginManager.getPluginByClass(it) }
    if (callerClass == null || requester == null) {
      LOG.infoWithDebug("Permission request ${request.javaClass.name} is rejected: " +
                        "caller class ${callerClass?.name} does not belong to a plugin", requestTrace)
      return Result.failure(UnknownCallerPluginException())
    }

    @Suppress("UNCHECKED_CAST")
    val handler = handlers[request.javaClass] as? PluginPermissionHandler<PluginPermissionRequest<T>, T>
    if (handler == null) {
      LOG.infoWithDebug("Permission request ${request.javaClass.name} from ${requester.toLogString()} is rejected: " +
                        "the request type is not supported", requestTrace)
      return Result.failure(UnsupportedOperationException("Unsupported permission request: ${request.javaClass.name}"))
    }

    val permissionKey = handler.getPermissionKey(request)
    LOG.infoWithDebug("${requester.toLogString()} requests permission '$permissionKey' " +
                      "(${request.javaClass.name}) from ${callerClass.name}", requestTrace)

    val decision = decide(requester, request, handler, permissionKey)
    LOG.info("Permission '$permissionKey' for ${requester.toLogString()}: $decision")
    if (!decision.isGranted) {
      return Result.failure(PluginPermissionNotGrantedException("No permission granted by user"))
    }

    return try {
      val result = action(handler.createGrant(requester, request))
      LOG.info("Action of ${requester.toLogString()} with permission '$permissionKey' completed")
      Result.success(result)
    }
    catch (e: Throwable) {
      rethrowControlFlowException(e)
      LOG.infoWithDebug("Action of ${requester.toLogString()} with permission '$permissionKey' failed: $e", e)
      Result.failure(e)
    }
  }

  private suspend fun <T> decide(
    requester: PluginDescriptor,
    request: PluginPermissionRequest<T>,
    handler: PluginPermissionHandler<PluginPermissionRequest<T>, T>,
    permissionKey: String,
  ): PluginPermissionDecision {
    val storedKey = "${requester.pluginId.idString}/$permissionKey"
    if (synchronized(decisionLock) { state.alwaysAllowed.contains(storedKey) }) {
      return PluginPermissionDecision.ALLOWED_BY_STORED_DECISION
    }
    if (ApplicationManager.getApplication().isHeadlessEnvironment) {
      return PluginPermissionDecision.DENIED_IN_HEADLESS_ENVIRONMENT
    }

    val allow = IdeBundle.message("plugin.permission.button.allow")
    val allowAlways = IdeBundle.message("plugin.permission.button.allow.always")
    val deny = IdeBundle.message("plugin.permission.button.deny")
    // the handler can load data for the message, so get it before the switch to EDT
    val message = handler.getMessage(requester, request)
    val choice = withContext(Dispatchers.EDT) {
      MessageDialogBuilder.Message(IdeBundle.message("plugin.permission.dialog.title"), message)
        .buttons(allow, allowAlways, deny)
        .defaultButton(deny)
        .focusedButton(deny)
        .asWarning()
        .show()
    }
    return when (choice) {
      allow -> PluginPermissionDecision.ALLOWED_ONCE
      allowAlways -> {
        synchronized(decisionLock) {
          if (!state.alwaysAllowed.contains(storedKey)) {
            state.alwaysAllowed.add(storedKey)
          }
        }
        PluginPermissionDecision.ALLOWED_ALWAYS
      }
      deny -> PluginPermissionDecision.DENIED
      else -> PluginPermissionDecision.DIALOG_CLOSED
    }
  }
}

/**
 * The result of a permission check. The service writes it to the log.
 */
private enum class PluginPermissionDecision(val isGranted: Boolean) {
  /** A stored "Allow Always" decision allows the request. The service shows no dialog. */
  ALLOWED_BY_STORED_DECISION(true),
  /** The user clicks "Allow". */
  ALLOWED_ONCE(true),
  /** The user clicks "Allow Always". The service stores the decision. */
  ALLOWED_ALWAYS(true),
  /** The user clicks "Deny". */
  DENIED(false),
  /** The user closes the dialog without a choice. */
  DIALOG_CLOSED(false),
  /** The environment is headless, and no stored "Allow Always" decision exists. */
  DENIED_IN_HEADLESS_ENVIRONMENT(false),
}

internal class PluginPermissionState : BaseState() {
  /** The "Allow Always" decisions. Each key has the form `<requesting plugin ID>/<permission key>`. */
  @get:XCollection
  val alwaysAllowed: MutableList<String> by list()
}

internal class UnknownCallerPluginException : RuntimeException()

/**
 * Tells if [aClass] is [PluginPermissionJavaShim] or one of its nested classes, such as its coroutine lambda.
 */
private fun isJavaShimClass(aClass: Class<*>): Boolean {
  val shimClassName = PluginPermissionJavaShim::class.java.name
  return aClass.name == shimClassName || aClass.name.startsWith("$shimClassName$")
}

internal fun PluginDescriptor.toLogString(): String = "plugin '$name' (${pluginId.idString}, version $version)"
