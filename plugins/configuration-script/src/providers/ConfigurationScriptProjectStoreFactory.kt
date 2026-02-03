package com.intellij.configurationScript.providers

import com.intellij.configurationScript.ConfigurationFileManager
import com.intellij.configurationScript.readIntoObject
import com.intellij.configurationStore.ComponentInfo
import com.intellij.configurationStore.ProjectStoreFactoryBase
import com.intellij.configurationStore.ProjectWithModuleStoreImpl
import com.intellij.configurationStore.SaveSessionProducer
import com.intellij.configurationStore.SaveSessionProducerBase
import com.intellij.configurationStore.StateGetter
import com.intellij.configurationStore.StoreReloadManager
import com.intellij.configurationStore.deserializeBaseStateWithCustomNameFilter
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.StateStorage
import com.intellij.openapi.components.impl.stores.IProjectStore
import com.intellij.openapi.components.impl.stores.stateStore
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.Project
import com.intellij.util.ReflectionUtil
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

internal class ConfigurationScriptProjectStoreFactory : ProjectStoreFactoryBase() {
  override fun createStore(project: Project): IProjectStore = MyProjectStore(project)
}

private class MyProjectStore(project: Project) : ProjectWithModuleStoreImpl(project) {
  @JvmField
  val isConfigurationFileListenerAdded: AtomicBoolean = AtomicBoolean()
  private val storages = ConcurrentHashMap<Class<Any>, ReadOnlyStorage>()

  fun configurationFileChanged() {
    if (storages.isNotEmpty()) {
      StoreReloadManager.getInstance(project).storageFilesChanged(store = project.stateStore, storages = storages.values.toList())
    }
  }

  override fun unloadComponent(component: Any) {
    super.unloadComponent(component)
    if (component is PersistentStateComponent<*>) {
      storages.remove(component.javaClass)
    }
  }

  override fun getReadOnlyStorage(componentClass: Class<Any>, stateClass: Class<Any>, configurationSchemaKey: String): StateStorage {
    // service container ensures that one key is never requested from different threads
    return storages.computeIfAbsent(componentClass) {
      ReadOnlyStorage(configurationSchemaKey = configurationSchemaKey, componentClass = componentClass, store = this)
    }
  }

  override fun doCreateStateGetter(
    reloadData: Boolean,
    storage: StateStorage,
    info: ComponentInfo,
    componentName: String,
    stateClass: Class<Any>,
    useLoadedStateAsExisting: Boolean,
  ): StateGetter<Any> {
    val stateGetter = super.doCreateStateGetter(
      reloadData = reloadData,
      storage = storage,
      info = info,
      componentName = componentName,
      stateClass = stateClass,
      useLoadedStateAsExisting = useLoadedStateAsExisting,
    )
    val configurationSchemaKey = info.configurationSchemaKey ?: return stateGetter
    val configurationFileManager = ConfigurationFileManager.getInstance(project)
    val node = configurationFileManager.findValueNode(configurationSchemaKey) ?: return stateGetter
    return object : StateGetter<Any> {
      override suspend fun getState(mergeInto: Any?): Any {
        val state = stateGetter.getState(mergeInto) ?: ReflectionUtil.newInstance(stateClass, false)
        val affectedProperties = mutableListOf<String>()
        readIntoObject(instance = state as BaseState, nodes = node) { affectedProperties.add(it.name!!) }
        info.affectedPropertyNames = affectedProperties
        return state
      }

      override fun archiveState(): Any? {
        // feature "preventing inappropriate state modification" is disabled for workspace components,
        // also, this feature makes little sense for properly implemented PersistenceStateComponent using BaseState
        return null
      }
    }
  }

  // Generally, this method isn't required in this form
  // because SaveSessionBase.setState accepts serialized state (Element) without any side effects or performance degradation.
  // However, it's preferable to express contracts explicitly in the code to ensure they aren't inadvertently broken in the future.
  override fun setStateToSaveSessionProducer(
    state: Any?,
    info: ComponentInfo,
    effectiveComponentName: String,
    sessionProducer: SaveSessionProducer,
  ) {
    val configurationSchemaKey = info.configurationSchemaKey
    if (state == null ||
        configurationSchemaKey == null ||
        info.affectedPropertyNames.isEmpty() ||
        sessionProducer !is SaveSessionProducerBase) {
      super.setStateToSaveSessionProducer(
        state = state,
        info = info,
        effectiveComponentName = effectiveComponentName,
        sessionProducer = sessionProducer,
      )
    }
    else {
      val serializedState = deserializeBaseStateWithCustomNameFilter(state as BaseState, info.affectedPropertyNames)
      sessionProducer.setSerializedState(componentName = effectiveComponentName, element = serializedState)
    }
  }

  override suspend fun reload(changedStorages: Set<StateStorage>): Collection<String>? {
    val result = super.reload(changedStorages)
    for (storage in changedStorages) {
      if (storage !is ReadOnlyStorage) {
        continue
      }

      @Suppress("IncorrectServiceRetrieving")
      val component = project.getServiceIfCreated(storage.componentClass)
      if (component == null) {
        logger<ConfigurationScriptProjectStoreFactory>().error("Cannot find component by ${storage.componentClass.name}")
        continue
      }

      @Suppress("UNCHECKED_CAST")
      initComponentWithoutStateSpec(
        component = component as PersistentStateComponent<Any>,
        configurationSchemaKey = storage.configurationSchemaKey,
        // todo not clear, is it ok, anyway, configuration script is used mostly for Core
        pluginId = PluginManagerCore.CORE_ID,
      ) {
        it()
      }
    }
    return result
  }
}

private class ReadOnlyStorage(
  @JvmField val configurationSchemaKey: String,
  @JvmField val componentClass: Class<Any>,
  private val store: MyProjectStore,
) : StateStorage {
  override fun <T : Any> getState(
    component: Any?,
    componentName: String,
    pluginId: PluginId,
    stateClass: Class<T>,
    mergeInto: T?,
    reload: Boolean,
  ): T {
    val state = ReflectionUtil.newInstance(stateClass, false) as BaseState

    val configurationFileManager = ConfigurationFileManager.getInstance(store.project)
    if (store.isConfigurationFileListenerAdded.compareAndSet(false, true)) {
      configurationFileManager.registerClearableLazyValue {
        store.configurationFileChanged()
      }
    }

    val node = configurationFileManager.findValueNode(configurationSchemaKey)
    if (node != null) {
      readIntoObject(state, node)
    }
    @Suppress("UNCHECKED_CAST")
    return state as T
  }

  override fun createSaveSessionProducer(): SaveSessionProducer? = null

  override suspend fun analyzeExternalChangesAndUpdateIfNeeded(componentNames: MutableSet<in String>) {
  }
}
