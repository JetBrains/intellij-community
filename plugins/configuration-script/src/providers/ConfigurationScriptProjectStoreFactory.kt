package com.intellij.configurationScript.providers

import com.intellij.configurationScript.ConfigurationFileManager
import com.intellij.configurationScript.readIntoObject
import com.intellij.configurationStore.*
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.StateStorage
import com.intellij.openapi.components.impl.stores.IComponentStore
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.impl.ProjectStoreFactory
import com.intellij.util.ReflectionUtil
import org.snakeyaml.engine.v2.nodes.NodeTuple

internal class ConfigurationScriptProjectStoreFactory : ProjectStoreFactory {
  override fun createStore(project: Project): IComponentStore {
    return if (project.isDefault) DefaultProjectStoreImpl(project) else MyProjectStore(project)
  }
}

private class MyProjectStore(project: Project) : ProjectWithModulesStoreImpl(project) {

  override fun doCreateStateGetter(reloadData: Boolean,
                                   storage: StateStorage,
                                   info: ComponentInfo,
                                   name: String,
                                   stateClass: Class<Any>): StateGetter<Any> {
    val stateGetter = super.doCreateStateGetter(reloadData, storage, info, name, stateClass)
    val configurationSchemaKey = info.configurationSchemaKey ?: return stateGetter
    val node = ConfigurationFileManager.getInstance(project).findValueNode(configurationSchemaKey) ?: return stateGetter
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
}

internal fun <T : BaseState> readComponentConfiguration(nodes: List<NodeTuple>, stateClass: Class<out T>): T? {
  return readIntoObject(ReflectionUtil.newInstance(stateClass), nodes)
}