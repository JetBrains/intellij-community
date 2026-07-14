// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.updateSettings.impl

import com.intellij.diagnostic.rethrowControlFlowException
import com.intellij.ide.IdeBundle
import com.intellij.ide.impl.ProjectUtil
import com.intellij.ide.plugins.PluginEnabler
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.plugins.newui.UiPluginManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.contextModality
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.extensions.PluginId
import com.intellij.ide.plugins.PluginPermissionNotGrantedException
import com.intellij.ide.plugins.PluginPermissionRequest
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.updateSettings.AccessPluginClassLoadersRequest
import com.intellij.openapi.updateSettings.DisablePluginRequest
import com.intellij.openapi.updateSettings.EnablePluginRequest
import com.intellij.openapi.updateSettings.InstallPluginRequest
import com.intellij.openapi.updateSettings.PluginManagementAction
import com.intellij.openapi.updateSettings.ReadPluginDescriptorsAction
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.InstallAndEnableTask
import com.intellij.openapi.util.NlsContexts
import com.intellij.platform.ide.progress.TaskCancellation
import com.intellij.platform.ide.progress.withBackgroundProgress
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import org.jetbrains.annotations.NonNls
import java.util.concurrent.atomic.AtomicBoolean

// the same category as the service, so that one debug setting covers the whole feature
private val LOG = logger<PluginPermissionServiceImpl>()

/**
 * Processes one type of [PluginPermissionRequest] for [PluginPermissionServiceImpl].
 *
 * The service asks the user and stores the "Allow Always" decision.
 * The handler describes the request and creates the object that the action of the requesting plugin receives.
 */
internal interface PluginPermissionHandler<Q : PluginPermissionRequest<T>, T> {
  /** The request class that this handler processes. */
  val requestClass: Class<Q>

  /**
   * Returns the key of the "Allow Always" decision for [request].
   * The key does not contain the requesting plugin, because the service adds it.
   */
  fun getPermissionKey(request: Q): @NonNls String

  /**
   * Returns the text that tells the user what [requester] asks for.
   */
  suspend fun getMessage(requester: PluginDescriptor, request: Q): @NlsContexts.DialogMessage String

  /**
   * Creates the object that the service gives to the action of [requester] after the user allows [request].
   * The object must be valid for one call only. Use [SingleUseGrant] for this.
   */
  fun createGrant(requester: PluginDescriptor, request: Q): T
}

/**
 * Installs a plugin from the plugin repositories and enables it.
 */
internal class InstallPluginPermissionHandler(
  private val coroutineScope: CoroutineScope,
) : PluginPermissionHandler<InstallPluginRequest, PluginManagementAction> {
  override val requestClass: Class<InstallPluginRequest> = InstallPluginRequest::class.java

  override fun getPermissionKey(request: InstallPluginRequest): String = "install:${request.pluginId.idString}"

  /**
   * Shows the name of the plugin to install together with its ID.
   * When the name lookup fails or the user cancels it, the text shows only the ID.
   */
  override suspend fun getMessage(requester: PluginDescriptor, request: InstallPluginRequest): String {
    val pluginId = request.pluginId
    val pluginName = findPluginName(pluginId)
    return if (pluginName == null) {
      IdeBundle.message("plugin.permission.install.unknown.message", requester.name, pluginId.idString, request.message)
    }
    else {
      IdeBundle.message("plugin.permission.install.message", requester.name, pluginName, pluginId.idString, request.message)
    }
  }

  /**
   * Finds the name of [pluginId] in the installed plugins and in the plugin repositories.
   *
   * The lookup can send a request to the repositories, so it shows cancellable background progress in the active project.
   * When no project is open, the lookup shows no progress and the user cannot cancel it.
   *
   * @return the name, or `null` when the lookup finds no name, fails, or the user cancels it.
   */
  private suspend fun findPluginName(pluginId: PluginId): String? {
    // The default controller sends a blocking request, and a blocking call does not stop on cancellation.
    // Thus, the lookup is a separate job, and the progress only waits for it. A cancel does not wait for the network.
    val nameLookup = coroutineScope.async(Dispatchers.IO) { loadPluginName(pluginId) }
    val project = ProjectUtil.getActiveProject() ?: ProjectManager.getInstance().openProjects.firstOrNull()
    val progress = if (project == null) {
      nameLookup
    }
    else {
      val title = IdeBundle.message("plugin.permission.install.progress.title", pluginId.idString)
      coroutineScope.async {
        withBackgroundProgress(project, title, TaskCancellation.cancellable()) {
          nameLookup.await()
        }
      }
    }

    try {
      // join() does not throw when the progress is canceled, only when the caller is canceled
      progress.join()
    }
    finally {
      progress.cancel()
      nameLookup.cancel()
    }

    if (progress.isCancelled) {
      LOG.info("Lookup of the name of plugin ${pluginId.idString} to install is canceled")
      return null
    }
    val name = progress.await()
    LOG.info("Name of plugin ${pluginId.idString} to install: $name")
    // findPluginNames returns the ID when it finds no name
    return name?.takeIf { it != pluginId.idString }
  }

  private suspend fun loadPluginName(pluginId: PluginId): String? {
    return try {
      UiPluginManager.getInstance().getController().findPluginNames(listOf(pluginId)).firstOrNull()
    }
    catch (e: Throwable) {
      rethrowControlFlowException(e)
      LOG.infoWithDebug("Cannot find the name of plugin ${pluginId.idString}: $e", e)
      null
    }
  }

  override fun createGrant(requester: PluginDescriptor, request: InstallPluginRequest): PluginManagementAction {
    val grant = SingleUseGrant(requester, getPermissionKey(request))
    return PluginManagementAction {
      grant.use()
      LOG.info("${requester.toLogString()} starts the installation of plugin ${request.pluginId.idString}")
      coroutineScope.launch {
        InstallAndEnableTask(
          project = null,
          pluginIds = setOf(request.pluginId),
          showDialog = true,
          selectAllInDialog = true,
          modalityState = coroutineContext.contextModality() ?: ModalityState.nonModal(),
          onSuccess = { LOG.info("Plugin ${request.pluginId.idString} is installed on request of ${requester.toLogString()}") },
        ).execute()
      }
    }
  }
}

/**
 * Enables an installed plugin.
 * The plugin loads without an IDE restart when the plugin supports it.
 */
internal class EnablePluginPermissionHandler(
  private val coroutineScope: CoroutineScope,
) : PluginPermissionHandler<EnablePluginRequest, PluginManagementAction> {
  override val requestClass: Class<EnablePluginRequest> = EnablePluginRequest::class.java

  override fun getPermissionKey(request: EnablePluginRequest): String = "enable:${request.pluginId.idString}"

  override suspend fun getMessage(requester: PluginDescriptor, request: EnablePluginRequest): String {
    return IdeBundle.message("plugin.permission.enable.message", requester.name, getPluginName(request.pluginId), request.message)
  }

  override fun createGrant(requester: PluginDescriptor, request: EnablePluginRequest): PluginManagementAction {
    val grant = SingleUseGrant(requester, getPermissionKey(request))
    return PluginManagementAction {
      grant.use()
      LOG.info("${requester.toLogString()} enables plugin ${request.pluginId.idString}")
      // DynamicPlugins requires EDT
      coroutineScope.launch(Dispatchers.EDT) {
        val loaded = PluginEnabler.getInstance().enableById(setOf(request.pluginId))
        LOG.info("Plugin ${request.pluginId.idString} is enabled on request of ${requester.toLogString()}, " +
                 "loaded without restart: $loaded")
      }
    }
  }
}

/**
 * Disables a plugin.
 * The plugin unloads without an IDE restart when the plugin supports it.
 */
internal class DisablePluginPermissionHandler(
  private val coroutineScope: CoroutineScope,
) : PluginPermissionHandler<DisablePluginRequest, PluginManagementAction> {
  override val requestClass: Class<DisablePluginRequest> = DisablePluginRequest::class.java

  override fun getPermissionKey(request: DisablePluginRequest): String = "disable:${request.pluginId.idString}"

  override suspend fun getMessage(requester: PluginDescriptor, request: DisablePluginRequest): String {
    return IdeBundle.message("plugin.permission.disable.message", requester.name, getPluginName(request.pluginId), request.message)
  }

  override fun createGrant(requester: PluginDescriptor, request: DisablePluginRequest): PluginManagementAction {
    val grant = SingleUseGrant(requester, getPermissionKey(request))
    return PluginManagementAction {
      grant.use()
      LOG.info("${requester.toLogString()} disables plugin ${request.pluginId.idString}")
      // DynamicPlugins requires EDT
      coroutineScope.launch(Dispatchers.EDT) {
        val unloaded = PluginEnabler.getInstance().disableById(setOf(request.pluginId))
        LOG.info("Plugin ${request.pluginId.idString} is disabled on request of ${requester.toLogString()}, " +
                 "unloaded without restart: $unloaded")
      }
    }
  }
}

/**
 * Gives access to the descriptors of all installed plugins and to their class loaders.
 *
 * The handler logs each call of [ReadPluginDescriptorsAction.getPluginDescriptors], because the access is sensitive.
 */
internal class AccessPluginClassLoadersPermissionHandler
  : PluginPermissionHandler<AccessPluginClassLoadersRequest, ReadPluginDescriptorsAction> {
  override val requestClass: Class<AccessPluginClassLoadersRequest> = AccessPluginClassLoadersRequest::class.java

  override fun getPermissionKey(request: AccessPluginClassLoadersRequest): String = "access-class-loaders"

  override suspend fun getMessage(requester: PluginDescriptor, request: AccessPluginClassLoadersRequest): String {
    return IdeBundle.message("plugin.permission.access.class.loaders.message", requester.name, request.message)
  }

  override fun createGrant(requester: PluginDescriptor, request: AccessPluginClassLoadersRequest): ReadPluginDescriptorsAction {
    val grant = SingleUseGrant(requester, getPermissionKey(request))
    return ReadPluginDescriptorsAction {
      grant.use()
      val descriptors = PluginManagerCore.plugins.toList<PluginDescriptor>()
      LOG.info("${requester.toLogString()} reads ${descriptors.size} plugin descriptors with their class loaders")
      descriptors
    }
  }
}

/**
 * Lets the requesting plugin use a grant only once.
 *
 * Each allowed request creates a new grant.
 * To do the operation again, the plugin must send a new request.
 */
private class SingleUseGrant(private val requester: PluginDescriptor, private val permissionKey: String) {
  private val isUsed = AtomicBoolean(false)

  /**
   * Marks the grant as used.
   *
   * @throws PluginPermissionNotGrantedException when the plugin already used the grant.
   */
  fun use() {
    if (!isUsed.compareAndSet(false, true)) {
      val message = "${requester.toLogString()} uses permission '$permissionKey' again, but a grant can be used only once"
      val e = PluginPermissionNotGrantedException(message)
      LOG.warnWithDebug(message, e)
      throw e
    }
  }
}

private fun getPluginName(pluginId: PluginId): String = PluginManagerCore.getPlugin(pluginId)?.name ?: pluginId.idString
