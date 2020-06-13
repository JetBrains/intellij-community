package com.intellij.configurationScript.providers

import com.intellij.configurationScript.ConfigurationFileManager
import com.intellij.configurationScript.readIntoObject
import com.intellij.configurationStore.*
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.StateStorage
import com.intellij.openapi.components.impl.stores.IComponentStore
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.impl.ProjectStoreFactory
import com.intellij.util.ReflectionUtil
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

private class ConfigurationScriptProjectStoreFactory : ProjectStoreFactory {
  override fun createStore(project: Project): IComponentStore {
    return if (project.isDefault) DefaultProjectStoreImpl(project) else MyProjectStore(project)
  }
}

private class MyProjectStore(project: Project) : ProjectWithModulesStoreImpl(project) {
  val isConfigurationFileListenerAdded = AtomicBoolean()
  private val storages = ConcurrentHashMap<Class<Any>, ReadOnlyStorage>()

  fun configurationFileChanged() {
    if (storages.isNotEmpty()) {
      StoreReloadManager.getInstance().storageFilesChanged(mapOf(project to storages.values.toList()))
    }
  }

  override fun getReadOnlyStorage(componentClass: Class<Any>, stateClass: Class<Any>, configurationSchemaKey: String): StateStorage? {
    // service container ensures that one key is never requested from different threads
    return storages.getOrPut(componentClass) { ReadOnlyStorage(configurationSchemaKey, componentClass, this) }
  }

  override fun doCreateStateGetter(reloadData: Boolean,
                                   storage: StateStorage,
                                   info: ComponentInfo,
                                   name: String,
                                   stateClass: Class<Any>): StateGetter<Any> {
    val stateGetter = super.doCreateStateGetter(reloadData, storage, info, name, stateClass)
    val configurationSchemaKey = info.configurationSchemaKey ?: return stateGetter
    val configurationFileManager = ConfigurationFileManager.getInstance(project)
    val node = configurationFileManager.findValueNode(configurationSchemaKey) ?: return stateGetter
    return object : StateGetter<Any> {
      override fun getState(mergeInto: Any?): Any? {
        var state = stateGetter.getState(mergeInto)
        if (state == null) {
          state = ReflectionUtil.newInstance(stateClass, false)
        }

        val affectedProperties = mutableListOf<String>()
        readIntoObject(state as BaseState, node) { affectedProperties.add(it.name!!) }
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

  // In general this method is not required in this form, because SaveSessionBase.setState accepts serialized state (Element) without any side-effects or performance degradation,
  // but it is better to express contract in code to make sure that it will be not broken in the future.
  override fun setStateToSaveSessionProducer(state: Any?, info: ComponentInfo, effectiveComponentName: String, sessionProducer: SaveSessionProducer) {
    val configurationSchemaKey = info.configurationSchemaKey
    if (state == null || configurationSchemaKey == null || info.affectedPropertyNames.isEmpty() || sessionProducer !is SaveSessionBase) {
      super.setStateToSaveSessionProducer(state, info, effectiveComponentName, sessionProducer)
    }
    else {
      val serializedState = deserializeBaseStateWithCustomNameFilter(state as BaseState, info.affectedPropertyNames)
      sessionProducer.setSerializedState(effectiveComponentName, serializedState)
    }
  }

  override fun reload(changedStorages: Set<StateStorage>): Collection<String>? {
    val result = super.reload(changedStorages)
    for (storage in changedStorages) {
      if (storage !is ReadOnlyStorage) {
        continue
      }

      val component = project.getServiceIfCreated(storage.componentClass)
      if (component == null) {
        logger<ConfigurationScriptProjectStoreFactory>().error("Cannot find component by ${storage.componentClass.name}")
        continue
      }

      @Suppress("UNCHECKED_CAST")
      initComponentWithoutStateSpec(component as PersistentStateComponent<Any>, storage.configurationSchemaKey)
    }
    return result
  }
}

private class ReadOnlyStorage(val configurationSchemaKey: String, val componentClass: Class<Any>, private val store: MyProjectStore) : StateStorage {
  override fun <T : Any> getState(component: Any?, componentName: String, stateClass: Class<T>, mergeInto: T?, reload: Boolean): T? {
    val state = ReflectionUtil.newInstance(stateClass, false) as BaseState

    val configurationFileManager = ConfigurationFileManager.getInstance(store.project)
    if (store.isConfigurationFileListenerAdded.compareAndSet(false, true)) {
      configurationFileManager.registerClearableLazyValue {
        store.configurationFileChanged()
      }
    }

    val node = configurationFileManager.findValueNode(configurationSchemaKey) ?: return null
    readIntoObject(state, node)
    @Suppress("UNCHECKED_CAST")
    return state as T
  }

  // never called for read-only storage
  override fun hasState(componentName: String, reloadData: Boolean) = false

  override fun createSaveSessionProducer(): SaveSessionProducer? = null

  override fun analyzeExternalChangesAndUpdateIfNeeded(componentNames: MutableSet<String>) {
  }
}