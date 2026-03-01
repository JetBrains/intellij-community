// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment", "ReplaceGetOrSet")
@file:OptIn(IntellijInternalApi::class, SettingsInternalApi::class)

package com.intellij.platform.settings.local

import com.intellij.configurationStore.SaveSession
import com.intellij.configurationStore.SaveSessionProducer
import com.intellij.configurationStore.__platformSerializer
import com.intellij.configurationStore.jdomSerializer
import com.intellij.openapi.components.StateStorage
import com.intellij.openapi.components.impl.stores.ComponentStorageUtil
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.IntellijInternalApi
import com.intellij.platform.settings.JsonElementSettingSerializerDescriptor
import com.intellij.platform.settings.SetResult
import com.intellij.platform.settings.SettingDescriptor
import com.intellij.platform.settings.SettingTag
import com.intellij.serialization.SerializationException
import com.intellij.serialization.xml.deserializeAsJdomElement
import com.intellij.util.xmlb.BeanBinding
import com.intellij.util.xmlb.Binding
import com.intellij.util.xmlb.KotlinxSerializationBinding
import com.intellij.util.xmlb.SettingsInternalApi
import com.intellij.util.xmlb.XmlSerializationException
import com.intellij.util.xmlb.isPropertySkipped
import com.intellij.util.xmlb.jdomToJson
import com.intellij.util.xmlb.normalizePropertyNameForKotlinx
import kotlinx.serialization.json.JsonElement
import org.jdom.Element
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType

internal class StateStorageBackedByController(
  @JvmField val controller: SettingsControllerMediator,
  private val tags: List<SettingTag>,
) : StateStorage {
  override fun <T : Any> getState(component: Any?, componentName: String, pluginId: PluginId, stateClass: Class<T>, mergeInto: T?, reload: Boolean): T? {
    @Suppress("DEPRECATION", "UNCHECKED_CAST")
    when {
      stateClass === Element::class.java -> {
        return deserializeAsJdomElement(localValue = null, controller = controller, componentName = componentName, pluginId = pluginId, tags = tags) as T?
      }
      com.intellij.openapi.util.JDOMExternalizable::class.java.isAssignableFrom(stateClass) -> {
        return readDataForDeprecatedJdomExternalizable(componentName = componentName, mergeInto = mergeInto, stateClass = stateClass, pluginId = pluginId)
      }
      else -> {
        try {
          val rootBinding = __platformSerializer().getRootBinding(stateClass)
          if (rootBinding is KotlinxSerializationBinding) {
            val data = controller.getItem(createSettingDescriptor(componentName, pluginId)) ?: return null
            return rootBinding.fromJson(currentValue = null, element = data) as T
          }
          else {
            return getXmlSerializationState(mergeInto = mergeInto, beanBinding = rootBinding, componentName = componentName, pluginId = pluginId)
          }
        }
        catch (e: SerializationException) {
          throw e
        }
        catch (e: Exception) {
          throw XmlSerializationException("Cannot deserialize class ${stateClass.name}", e)
        }
      }
    }
  }

  private fun <T : Any> readDataForDeprecatedJdomExternalizable(componentName: String, pluginId: PluginId, mergeInto: T?, stateClass: Class<T>): T? {
    val data = deserializeAsJdomElement(localValue = null, controller = controller, componentName = componentName, pluginId = pluginId, tags = tags) ?: return mergeInto
    if (mergeInto != null) {
      thisLogger().error("State is ${stateClass.name}, merge into is $mergeInto, state element text is $data")
    }

    @Suppress("DEPRECATION")
    val t = MethodHandles.privateLookupIn(stateClass, MethodHandles.lookup())
      .findConstructor(stateClass, MethodType.methodType(Void.TYPE))
      .invoke() as com.intellij.openapi.util.JDOMExternalizable
    t.readExternal(data)
    @Suppress("UNCHECKED_CAST")
    return t as T
  }

  private fun <T : Any> getXmlSerializationState(mergeInto: T?, beanBinding: Binding, componentName: String, pluginId: PluginId): T? {
    var result = mergeInto
    val bindings = (beanBinding as BeanBinding).bindings!!
    for (binding in bindings) {
      val key = createSettingDescriptor("$componentName.${normalizePropertyNameForKotlinx(binding)}", pluginId)
      val item = try {
        controller.doGetItem(key)
      }
      catch (e: Throwable) {
        thisLogger().error("Cannot deserialize value for $key", e)
        // exclusive storage - no fallback to old XML-based storage
        continue
      }

      if (!item.isResolved) {
        continue
      }

      val element = item.get() ?: continue
      if (result == null) {
        // create a result only if we have some data - do not return empty state class
        @Suppress("UNCHECKED_CAST")
        result = beanBinding.newInstance() as T
      }

      binding.setFromJson(result, element)
    }
    return result
  }

  override fun createSaveSessionProducer(): SaveSessionProducer {
    return ControllerBackedSaveSessionProducer(storageController = this)
  }

  override suspend fun analyzeExternalChangesAndUpdateIfNeeded(componentNames: MutableSet<in String>) {
    // external change is not expected and not supported
  }

  internal fun createSettingDescriptor(key: String, pluginId: PluginId): SettingDescriptor<JsonElement> {
    return SettingDescriptor(key = key, pluginId = pluginId, tags = tags, serializer = JsonElementSettingSerializerDescriptor)
  }
}

private class ControllerBackedSaveSessionProducer(
  private val storageController: StateStorageBackedByController,
) : SaveSessionProducer {
  private fun put(key: SettingDescriptor<JsonElement>, value: JsonElement?) {
    storageController.controller.setItem(key, value)
  }

  override fun setState(component: Any?, componentName: String, pluginId: PluginId, state: Any?) {
    if (state == null) {
      return
    }

    val settingDescriptor = storageController.createSettingDescriptor(componentName, pluginId)
    @Suppress("DEPRECATION")
    when (state) {
      is Element -> {
        if (!state.isEmpty) {
          put(settingDescriptor, jdomToJson(state))
        }
      }
      is com.intellij.openapi.util.JDOMExternalizable -> {
        val element = Element(ComponentStorageUtil.COMPONENT)
        state.writeExternal(element)
        if (!element.isEmpty) {
          put(settingDescriptor, jdomToJson(element))
        }
      }
      else -> {
        val aClass = state.javaClass
        val rootBinding = __platformSerializer().getRootBinding(aClass)
        if (rootBinding is KotlinxSerializationBinding) {
          put(settingDescriptor, rootBinding.toJson(bean = state, filter = null))
        }
        else {
          val filter = jdomSerializer.getDefaultSerializationFilter()
          for (binding in (rootBinding as BeanBinding).bindings!!) {
            val isPropertySkipped = isPropertySkipped(filter = filter, binding = binding, bean = state, rootBinding = rootBinding, isFilterPropertyItself = true)
            val key = settingDescriptor.withSubName(normalizePropertyNameForKotlinx(binding))
            val result = storageController.controller.doSetItem(key = key, value = if (isPropertySkipped) null else binding.toJson(state, filter))
            if (result != SetResult.inapplicable()) {
              continue
            }
          }
        }
      }
    }
  }

  override fun createSaveSession(): SaveSession? = null
}