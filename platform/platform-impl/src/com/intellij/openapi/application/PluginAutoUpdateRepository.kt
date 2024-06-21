// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application

import com.intellij.ide.plugins.IdeaPluginDescriptorImpl
import com.intellij.ide.plugins.PluginInstaller
import com.intellij.ide.plugins.PluginLoadingResult
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.plugins.isBrokenPlugin
import com.intellij.ide.plugins.loadDescriptorFromArtifact
import com.intellij.ide.plugins.loadDescriptors
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.PluginAutoUpdateRepository.PluginUpdateInfo
import com.intellij.openapi.application.PluginAutoUpdateRepository.PluginUpdatesData
import com.intellij.openapi.application.PluginAutoUpdateRepository.getAutoUpdateDirPath
import com.intellij.openapi.application.impl.ApplicationInfoImpl
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.getOrLogException
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.updateSettings.impl.PluginDownloader
import com.intellij.platform.diagnostic.telemetry.impl.span
import com.intellij.platform.ide.bootstrap.ZipFilePoolImpl
import com.intellij.ui.ComboboxSpeedSearch
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.text
import com.intellij.ui.dsl.listCellRenderer.textListCellRenderer
import com.intellij.util.cancelOnDispose
import com.intellij.util.io.copy
import com.intellij.util.io.createDirectories
import com.intellij.util.io.delete
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jetbrains.annotations.ApiStatus
import java.awt.Dimension
import java.nio.file.Files
import java.nio.file.Path
import java.util.Collections
import javax.swing.JComponent
import kotlin.Result
import kotlin.io.path.absolutePathString
import kotlin.io.path.deleteExisting
import kotlin.io.path.exists
import kotlin.io.path.isReadable
import kotlin.io.path.isRegularFile

@ApiStatus.Internal
object PluginAutoUpdateRepository {
  const val PLUGIN_AUTO_UPDATE_DIRECTORY_NAME: String = "plugins-auto-update"
  private const val STATE_FILE_NAME: String = ".autoupdate.data"

  fun getAutoUpdateDirPath(): Path = PathManager.getStartupScriptDir().resolve(PLUGIN_AUTO_UPDATE_DIRECTORY_NAME)

  private fun getAutoUpdateStatePath(): Path = getAutoUpdateDirPath().resolve(STATE_FILE_NAME)

  @Synchronized
  private fun clearState() {
    if (getAutoUpdateStatePath().isRegularFile()) {
      getAutoUpdateStatePath().deleteExisting()
    }
  }

  @Synchronized
  internal fun clearUpdates() {
    if (getAutoUpdateDirPath().exists()) {
      getAutoUpdateDirPath().delete(recursively = true)
    }
  }

  @Synchronized
  fun readUpdates(): Map<PluginId, PluginUpdateInfo> {
    val stateFile = getAutoUpdateStatePath()
    if (!stateFile.isReadable()) {
      return Collections.emptyMap<PluginId, PluginUpdateInfo>()
    }
    val updatesRaw = Files.readString(stateFile)
    return json.decodeFromString<PluginUpdatesData>(updatesRaw).updates.mapKeys { PluginId.getId(it.key) }
  }

  @Synchronized
  private fun writeUpdates(updatesData: PluginUpdatesData) {
    val dir = getAutoUpdateDirPath()
    if (!dir.exists()) {
      dir.createDirectories()
    }
    val updatesRaw = json.encodeToString(PluginUpdatesData.serializer(), updatesData)
    Files.writeString(getAutoUpdateStatePath(), updatesRaw)
  }

  @Synchronized
  fun addUpdates(updates: Map<PluginId, PluginUpdateInfo>) {
    val existing = readUpdates()
    val result: Map<PluginId, PluginUpdateInfo> = existing + updates
    writeUpdates(PluginUpdatesData(result.mapKeys { it.key.idString }))
  }

  suspend fun safeConsumeUpdates(logDeferred: Deferred<Logger>): Map<PluginId, PluginUpdateInfo> {
    val (readResult, clearResult) = synchronized(this) {
      runCatching { readUpdates() } to runCatching { clearState() }
    }
    val updates = readResult.getOrLogException { logDeferred.await().warn("Failed to read updates", it) } ?: emptyMap()
    clearResult.getOrLogException { logDeferred.await().warn("Failed to clear plugin auto update state", it) }
    return updates
  }

  /**
   * This method is called during startup, before the plugins are loaded.
   */
  suspend fun applyPluginUpdates(logDeferred: Deferred<Logger>) {
    val updates = safeConsumeUpdates(logDeferred).filter { (_, info) ->
      runCatching {
        Path.of(info.pluginPath).exists() && getAutoUpdateDirPath().resolve(info.updateFilename).exists()
      }.getOrElse { e ->
        logDeferred.await().warn(e)
        false
      }
    }
    runCatching {
      applyPluginUpdates(updates, logDeferred)
    }.getOrLogException { e ->
      logDeferred.await().error("Error occurred during application of plugin updates", e)
    }
    runCatching {
      clearUpdates()
    }.getOrLogException { e ->
      logDeferred.await().warn("Failed to clear plugin auto update directory", e)
    }
  }

  private suspend fun applyPluginUpdates(updates: Map<PluginId, PluginUpdateInfo>, logDeferred: Deferred<Logger>) {
    if (updates.isEmpty()) {
      return
    }
    logDeferred.await().info("There are ${updates.size} prepared updates for plugins. Applying...")
    val autoupdatesDir = getAutoUpdateDirPath()

    val currentDescriptors = span("loading existing descriptors") {
      val pool = ZipFilePoolImpl()
      val result = loadDescriptors(
        CompletableDeferred(pool),
        CompletableDeferred(PluginAutoUpdateRepository::class.java.classLoader)
      )
      pool.clear()
      result.second
    }
    // shadowing intended
    val updates = updates.filter { (id, _) ->
      (!PluginManagerCore.isDisabled(id) && (currentDescriptors.getIdMap()[id] != null || currentDescriptors.getIncompleteIdMap()[id] != null))
        .also { pluginForUpdateExists ->
          if (!pluginForUpdateExists) logDeferred.await().warn("Update for plugin $id is declined since the plugin is not going to be loaded")
        }
    }
    val updateDescriptors = span("update descriptors loading") {
      updates.mapValues { (_, info) ->
        val updateFile = autoupdatesDir.resolve(info.updateFilename)
        async(Dispatchers.IO) {
          runCatching { loadDescriptorFromArtifact(updateFile, null) }
        }
      }.mapValues { it.value.await() }
    }.filter {
      (it.value.getOrNull() != null).also { loaded ->
        if (!loaded) logDeferred.await().warn("Update for plugin ${it.key} has failed to load", it.value.exceptionOrNull())
      }
    }.mapValues { it.value.getOrNull()!! }

    val updateCheck = determineValidUpdates(currentDescriptors, updateDescriptors)
    updateCheck.rejectedUpdates.forEach { (id, reason) ->
      logDeferred.await().warn("Update for plugin $id has been rejected: $reason")
    }
    for (id in updateCheck.updatesToApply) {
      val update = updates[id]!!
      runCatching {
        val pluginPath = Path.of(update.pluginPath)
        if (pluginPath.exists()) {
          pluginPath.delete(true)
        }
        PluginInstaller.unpackPlugin(getAutoUpdateDirPath().resolve(update.updateFilename), pluginPath.parent)
      }.onFailure {
        logDeferred.await().warn("Failed to apply update for plugin $id", it)
      }.onSuccess {
        logDeferred.await().info("Plugin $id has been successfully updated: " +
                                 "version ${currentDescriptors.getIdMap()[id]?.version} -> ${updateDescriptors[id]!!.version}")
      }
    }
  }

  private data class UpdateCheckResult(
    val updatesToApply: Set<PluginId>,
    val rejectedUpdates: Map<PluginId, String>,
  )

  private fun determineValidUpdates(
    currentDescriptors: PluginLoadingResult,
    updates: Map<PluginId, IdeaPluginDescriptorImpl>,
  ): UpdateCheckResult {
    val updatesToApply = mutableSetOf<PluginId>()
    val rejectedUpdates = mutableMapOf<PluginId, String>()
    // checks mostly duplicate what is written in com.intellij.ide.plugins.PluginInstaller.installFromDisk. FIXME, I guess
    for ((id, updateDesc) in updates) {
      val existingDesc = currentDescriptors.getIdMap()[id] ?: currentDescriptors.getIncompleteIdMap()[id]
      if (existingDesc == null) {
        rejectedUpdates[id] = "plugin $id is not installed"
        continue
      }
      // no third-party plugin check, settings are not available at this point; that check must be done when downloading the updates
      if (PluginManagerCore.isIncompatible(updateDesc)) {
        rejectedUpdates[id] = "plugin $id of version ${updateDesc.version} is not compatible with current IDE build"
        continue
      }
      if (isBrokenPlugin(updateDesc)) {
        rejectedUpdates[id] = "plugin $id of version ${updateDesc.version} is known to be broken"
        continue
      }
      if (ApplicationInfoImpl.getShadowInstance().isEssentialPlugin(id)) {
        rejectedUpdates[id] = "plugin $id is part of the IDE distribution and cannot be updated without IDE update"
        continue
      }
      if (PluginDownloader.compareVersionsSkipBrokenAndIncompatible(updateDesc.version, existingDesc) <= 0) {
        rejectedUpdates[id] = "plugin $id has same or newer version installed (${existingDesc.version} vs update version ${updateDesc.version})"
        continue
      }
      // TODO check plugin dependencies are satisfied ? such functionality must be extracted into a single place
      // TODO check signature ? com.intellij.ide.plugins.marketplace.PluginSignatureChecker; probably also should be done after download
      updatesToApply.add(id)
    }
    return UpdateCheckResult(updatesToApply, rejectedUpdates)
  }

  /**
   * @param updates key is the plugin id
   */
  @Serializable
  data class PluginUpdatesData(val updates: Map<String, PluginUpdateInfo>)

  /**
   * @param pluginPath absolute path to the plugin that will be updated (it may be both bundled and non-bundled plugin)
   * @param updateFilename plugin update location in the plugins-auto-update directory
   */
  @Serializable
  data class PluginUpdateInfo(val pluginPath: String, val updateFilename: String)

  private val json = Json { ignoreUnknownKeys = true }
}

/**
 * Internal action for debugging purposes
 */
@Suppress("unused")
private class PluginsAutoUpdateRepositoryViewAction : AnAction() {
  override fun actionPerformed(e: AnActionEvent) {
    ApplicationManager.getApplication().invokeLater({ Viewer(e.project).showAndGet() }, ModalityState.nonModal())
  }

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = ApplicationManager.getApplication().isInternal
  }

  override fun isDumbAware(): Boolean = true
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  @Service(Service.Level.APP)
  private class ViewModel(val cs: CoroutineScope) {
    private val _state: MutableStateFlow<Result<Map<PluginId, PluginUpdateInfo>>?> = MutableStateFlow(null)
    val state: StateFlow<Result<Map<PluginId, PluginUpdateInfo>>?> = _state.asStateFlow()

    init {
      updateState()
    }

    private fun updateState() {
      cs.launch(Dispatchers.IO) {
        _state.value = runCatching { PluginAutoUpdateRepository.readUpdates() }
      }
    }

    fun clear() {
      cs.launch(Dispatchers.IO) {
        PluginAutoUpdateRepository.clearUpdates()
        updateState()
      }
    }

    fun add(pluginDescriptor: IdeaPluginDescriptorImpl, updatePath: Path) {
      cs.launch(Dispatchers.IO) {
        updatePath.copy(getAutoUpdateDirPath().resolve(updatePath.fileName))
        PluginAutoUpdateRepository.addUpdates(mapOf(
          pluginDescriptor.pluginId to PluginUpdateInfo(pluginDescriptor.path.absolutePathString(), updatePath.fileName.toString())
        ))
        updateState()
      }
    }
  }

  @Suppress("HardCodedStringLiteral")
  private class Viewer(val project: Project?) : DialogWrapper(project) {
    init {
      init()
    }

    override fun createCenterPanel(): JComponent? = panel {
      row("State") {
        val textArea = textArea()
        textArea.align(AlignX.FILL).applyToComponent {
          isEditable = false
          maximumSize = Dimension(700, 400)
        }
        service<ViewModel>().cs.launch(Dispatchers.EDT + ModalityState.stateForComponent(window).asContextElement()) {
          service<ViewModel>().state.collectLatest { state ->
            when {
              state == null -> textArea.text("initializing...")
              state.isFailure -> textArea.text("error: ${state.exceptionOrNull()}")
              state.isSuccess -> textArea.text(
                state.getOrNull()!!.map { it.key.idString to it.value.updateFilename }.joinToString("\n") { "${it.first}: ${it.second}" }
              )
            }
          }
        }.cancelOnDispose(disposable, false)
      }
      separator()
      row {
        val pluginCombobox = comboBox(PluginManagerCore.getPluginSet().allPlugins, textListCellRenderer {
          it?.name ?: it?.pluginId?.idString ?: "<unnamed plugin>"
        })
        ComboboxSpeedSearch.installOn(pluginCombobox.component)
        val updateChooser = textFieldWithBrowseButton("Select Update File", project, FileChooserDescriptorFactory.createSingleFileDescriptor())
        button("Add") {
          val selectedPlugin = pluginCombobox.component.selectedItem as? IdeaPluginDescriptorImpl ?: return@button
          val updateFilePath = updateChooser.component.text
          val updateFile = Path.of(updateFilePath).takeIf { it.isRegularFile() } ?: return@button
          service<ViewModel>().add(selectedPlugin, updateFile)
        }
      }
      row {
        button("Clear") {
          service<ViewModel>().clear()
        }
      }
    }
  }
}