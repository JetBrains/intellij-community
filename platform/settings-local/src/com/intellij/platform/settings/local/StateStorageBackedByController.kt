// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment", "ReplaceGetOrSet")
@file:OptIn(IntellijInternalApi::class, ExperimentalSerializationApi::class)

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
import com.intellij.platform.settings.GetResult
import com.intellij.platform.settings.RawSettingSerializerDescriptor
import com.intellij.platform.settings.SettingDescriptor
import com.intellij.platform.settings.SettingTag
import com.intellij.serialization.SerializationException
import com.intellij.serialization.xml.KotlinAwareBeanBinding
import com.intellij.serialization.xml.KotlinxSerializationBinding
import com.intellij.util.xmlb.*
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import org.jdom.Element
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import java.lang.reflect.Type
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

internal class StateStorageBackedByController(
  @JvmField val controller: SettingsControllerMediator,
  private val tags: List<SettingTag>,
) : StateStorage {
  private val bindingProducer = BindingProducer()

  @OptIn(ExperimentalSerializationApi::class)
  override fun <T : Any> getState(
    component: Any?,
    componentName: String,
    pluginId: PluginId,
    stateClass: Class<T>,
    mergeInto: T?,
    reload: Boolean,
  ): T? {
    @Suppress("DEPRECATION", "UNCHECKED_CAST")
    when {
      stateClass === Element::class.java -> {
        getXmlData(createSettingDescriptor(componentName, pluginId)).takeIf { it.isResolved }?.let {
          return it.get() as T?
        }
        return mergeInto
      }
      com.intellij.openapi.util.JDOMExternalizable::class.java.isAssignableFrom(stateClass) -> {
        return readDataForDeprecatedJdomExternalizable(
          componentName = componentName,
          mergeInto = mergeInto,
          stateClass = stateClass,
          pluginId = pluginId,
        )
      }
      else -> {
        try {
          val beanBinding = bindingProducer.getRootBinding(stateClass)
          if (beanBinding is KotlinxSerializationBinding) {
            val data = controller.getItem(createSettingDescriptor(componentName, pluginId)) ?: return null
            return cborFormat.decodeFromByteArray(beanBinding.serializer, data) as T
          }
          else {
            return getXmlSerializationState(
              mergeInto = mergeInto,
              beanBinding = beanBinding,
              componentName = componentName,
              pluginId = pluginId,
            )
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

  private fun <T : Any> readDataForDeprecatedJdomExternalizable(
    componentName: String,
    pluginId: PluginId,
    mergeInto: T?,
    stateClass: Class<T>,
  ): T? {
    // we don't care about data from the old storage for deprecated JDOMExternalizable
    val data = getXmlData(createSettingDescriptor(componentName, pluginId)).get() ?: return mergeInto
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

  private fun <T : Any> getXmlSerializationState(
    mergeInto: T?,
    beanBinding: Binding,
    componentName: String,
    pluginId: PluginId,
  ): T? {
    var result = mergeInto
    val bindings = (beanBinding as BeanBinding).bindings!!
    for (binding in bindings) {
      val data = getXmlData(createSettingDescriptor("$componentName.${binding.accessor.name}", pluginId))
      if (!data.isResolved) {
        continue
      }

      val element = data.get()
      if (element != null) {
        if (result == null) {
          // create a result only if we have some data - do not return empty state class
          @Suppress("UNCHECKED_CAST")
          result = beanBinding.newInstance() as T
        }

        val l = deserializeBeanInto(result = result, element = element, binding = binding, checkAttributes = true)
        if (l != null) {
          (binding as MultiNodeBinding).deserializeList(result, l, JdomAdapter)
        }
      }
    }
    return result
  }

  private fun getXmlData(key: SettingDescriptor<ByteArray>): GetResult<Element> {
    try {
      val item = controller.doGetItem(key)
      if (item.isResolved) {
        return GetResult.resolved(decodeCborToXml(item.get() ?: return GetResult.resolved(null)))
      }
    }
    catch (e: Throwable) {
      thisLogger().error("Cannot deserialize value for $key", e)
    }
    // exclusive storage - no fallback to old XML-based storage
    return GetResult.resolved(null)
  }

  override fun createSaveSessionProducer(): SaveSessionProducer {
    return ControllerBackedSaveSessionProducer(bindingProducer = bindingProducer, storageController = this)
  }

  override fun analyzeExternalChangesAndUpdateIfNeeded(componentNames: MutableSet<in String>) {
    // external change is not expected and not supported
  }

  internal fun createSettingDescriptor(key: String, pluginId: PluginId): SettingDescriptor<ByteArray> {
    return SettingDescriptor(
      key = key,
      pluginId = pluginId,
      tags = tags,
      serializer = RawSettingSerializerDescriptor,
    )
  }
}

private class ControllerBackedSaveSessionProducer(
  private val bindingProducer: BindingProducer,
  private val storageController: StateStorageBackedByController,
) : SaveSessionProducer {
  private fun put(key: SettingDescriptor<ByteArray>, value: ByteArray?) {
    storageController.controller.setItem(key, value)
  }

  override fun setState(component: Any?, componentName: String, pluginId: PluginId, state: Any?) {
    val settingDescriptor = storageController.createSettingDescriptor(componentName, pluginId)
    if (state == null) {
      put(key = settingDescriptor, value = null)
      return
    }

    @Suppress("DEPRECATION")
    when (state) {
      is Element -> putJdomElement(settingDescriptor, state)
      is com.intellij.openapi.util.JDOMExternalizable -> {
        val element = Element(ComponentStorageUtil.COMPONENT)
        state.writeExternal(element)
        putJdomElement(settingDescriptor, element)
      }
      else -> {
        val aClass = state.javaClass
        val beanBinding = bindingProducer.getRootBinding(aClass)
        if (beanBinding is KotlinxSerializationBinding) {
          // `Serializable` is not intercepted - it is not used for regular settings that we want to support on a property level
          put(settingDescriptor, cborFormat.encodeToByteArray(beanBinding.serializer, state))
        }
        else {
          val filter = jdomSerializer.getDefaultSerializationFilter()
          for (binding in (beanBinding as BeanBinding).bindings!!) {
            if (state is SerializationFilter && !state.accepts(binding.accessor, state)) {
              continue
            }

            val element = beanBinding.serializeProperty(
              binding = binding,
              bean = state,
              parentElement = null,
              filter = filter,
              isFilterPropertyItself = true,
            )
            putJdomElement(settingDescriptor.withSubName(binding.accessor.name), element)
          }
        }
      }
    }
  }

  // do nothing if failed to serialize
  private fun putJdomElement(key: SettingDescriptor<ByteArray>, state: Element?) {
    if (state == null || state.isEmpty) {
      return
    }

    try {
      put(key, encodeXmlToCbor(state))
    }
    catch (e: Throwable) {
      thisLogger().error("Cannot serialize value for $key", e)
    }
  }

  override fun createSaveSession(): SaveSession? = null
}

private class BindingProducer : XmlSerializerImpl.XmlSerializerBase() {
  private val cache = HashMap<Class<*>, Binding>()
  private val cacheLock = ReentrantReadWriteLock()

  override fun getRootBinding(aClass: Class<*>): Binding {
    return cacheLock.read {
      // create cache only under write lock
      cache.get(aClass)
    } ?: cacheLock.write {
      cache.get(aClass)?.let {
        return it
      }

      createRootBinding(aClass = aClass)
    }
  }

  override fun getRootBinding(aClass: Class<*>, originalType: Type): Binding {
    require(aClass === originalType) {
      "Expect that class $aClass is same as originalType $originalType"
    }
    return getRootBinding(aClass)
  }

  private fun createRootBinding(aClass: Class<*>): Binding {
    @Suppress("DuplicatedCode")
    var binding = createClassBinding(aClass, null, aClass, this)
    if (binding == null) {
      if (aClass.isAnnotationPresent(Serializable::class.java)) {
        binding = KotlinxSerializationBinding(aClass)
      }
      else {
        binding = KotlinAwareBeanBinding(aClass)
      }
    }
    cache.put(aClass, binding)
    try {
      binding.init(aClass, __platformSerializer())
    }
    catch (e: Throwable) {
      cache.remove(aClass)
      throw e
    }
    return binding
  }
}
