// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.project.impl.shared

import com.intellij.ide.plugins.*
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.*
import com.intellij.openapi.extensions.PluginId
import com.intellij.util.xmlb.annotations.Property
import com.intellij.util.xmlb.annotations.Tag
import com.intellij.util.xmlb.annotations.XCollection
import com.intellij.util.xmlb.annotations.XMap
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path
import kotlin.io.path.invariantSeparatorsPathString

@ApiStatus.Internal
const val DYNAMIC_PLUGINS_SYNCHRONIZER_FILE_NAME: String = "p3-dynamic-plugins.xml"

/**
 * Synchronizes state of dynamically loaded and unloaded plugins between multiple IDE processes running in parallel.
 * Lists of plugins which were dynamically loaded or unloaded during this session are stored in [P3State], so when one process
 * loads or unloads a plugin, other processes are notified about that using the common settings synchronization mechanics.
 */
@State(name = "P3DynamicPlugins", storages = [Storage(DYNAMIC_PLUGINS_SYNCHRONIZER_FILE_NAME, roamingType = RoamingType.DISABLED,
                                                      usePathMacroManager = false)])
internal class P3DynamicPluginSynchronizer: PersistentStateComponent<P3State>, DynamicPluginListener {
  companion object {
    fun getInstance(): P3DynamicPluginSynchronizer = service()
  }

  private val otherProcesses = LinkedHashMap<String, P3PluginsState>()

  /** stores information about plugins dynamically loaded or unloaded in this process */
  private val thisProcess = P3PluginsState()

  /** stores information about plugins dynamically loaded or unloaded by other processes which is currently synchronized with this process */
  private val synchronizingFromOtherProcesses = P3PluginsState()
  private val lock = Any()

  private val configPath: String
    get() = PathManager.getConfigDir().invariantSeparatorsPathString

  override fun getState(): P3State {
    synchronized(lock) {
      val allProcesses = LinkedHashMap(otherProcesses)
      if (thisProcess.loadedPlugins.isNotEmpty() || thisProcess.unloadedPlugins.isNotEmpty()) {
        allProcesses[configPath] = P3PluginsState(LinkedHashMap(thisProcess.loadedPlugins),
                                                  LinkedHashSet(thisProcess.unloadedPlugins))
      }
      else {
        allProcesses.remove(configPath)
      }
      return P3State(allProcesses)
    }
  }

  override fun loadState(state: P3State) {
    val toLoad: Map<String, String>
    val toUnload: Set<String>
    synchronized(lock) {
      otherProcesses.clear()
      val configPath = configPath
      state.processes.filterTo(otherProcesses) { it.key != configPath }
      toLoad = otherProcesses.values
        .flatMapTo(HashSet()) { it.loadedPlugins.asSequence() }
        .filterNot { it.key in thisProcess.loadedPlugins }
        .associateBy({ it.key }, { it.value })
      toUnload = otherProcesses.values.flatMapTo(HashSet()) { it.unloadedPlugins } - thisProcess.unloadedPlugins
      synchronizingFromOtherProcesses.loadedPlugins.putAll(toLoad)
      synchronizingFromOtherProcesses.unloadedPlugins.addAll(toUnload)
    }

    ApplicationManager.getApplication().invokeLater {
      if (toUnload.isNotEmpty()) {
        PluginEnabler.getInstance().disableById(toUnload.mapTo(HashSet()) { PluginId.getId(it) })
      }
      if (toLoad.isNotEmpty()) {
        val descriptors = toLoad.mapNotNull {
          loadDescriptor(Path.of(it.value), false, PluginXmlPathResolver.DEFAULT_PATH_RESOLVER)
        }
        DynamicPlugins.loadPlugins(descriptors, null)
      }
    }
  }

  override fun pluginLoaded(pluginDescriptor: IdeaPluginDescriptor) {
    synchronized(lock) {
      val pluginId = pluginDescriptor.pluginId.idString
      thisProcess.unloadedPlugins.remove(pluginId)
      if (synchronizingFromOtherProcesses.loadedPlugins.remove(pluginId) == null) {
        thisProcess.loadedPlugins[pluginId] = pluginDescriptor.pluginPath.invariantSeparatorsPathString
      }
    }
  }

  override fun pluginUnloaded(pluginDescriptor: IdeaPluginDescriptor, isUpdate: Boolean) {
    synchronized(lock) {
      val pluginId = pluginDescriptor.pluginId.idString
      thisProcess.loadedPlugins.remove(pluginId)
      if (!synchronizingFromOtherProcesses.unloadedPlugins.remove(pluginId)) {
        thisProcess.unloadedPlugins.add(pluginId)
      }
    }
  }
}

internal data class P3State(
  @get:Property(surroundWithTag = false)
  @get:XMap(entryTagName = "process", keyAttributeName = "config-path")
  var processes: Map<String, P3PluginsState> = LinkedHashMap()
)

@Tag("plugins")
internal data class P3PluginsState(
  @get:Property(surroundWithTag = false)
  @get:XMap(entryTagName = "loaded-plugin", keyAttributeName = "plugin-id", valueAttributeName = "plugin-path")
  var loadedPlugins: MutableMap<String, String> = LinkedHashMap(),
  
  @get:Property(surroundWithTag = false)
  @get:XCollection(style = XCollection.Style.v2, elementName = "unloaded-plugin", valueAttributeName = "plugin-id")
  var unloadedPlugins: MutableSet<String> = LinkedHashSet()
)
