// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment", "ReplaceGetOrSet")
@file:OptIn(IntellijInternalApi::class, ExperimentalSerializationApi::class)

package com.intellij.platform.settings.local

import com.intellij.configurationStore.SaveSession
import com.intellij.configurationStore.SaveSessionProducer
import com.intellij.configurationStore.__platformSerializer
import com.intellij.configurationStore.jdomSerializer
import com.intellij.openapi.components.StateStorage
import com.intellij.openapi.components.impl.stores.FileStorageCoreUtil
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.IntellijInternalApi
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

private val shimPluginId = PluginId.getId("__controller_shim__")

internal class StateStorageBackedByController(
  @JvmField val controller: SettingsControllerMediator,
  private val tags: List<SettingTag>,
  private val oldStorage: XmlFileStorage?,
) : StateStorage {
  private val bindingProducer = BindingProducer()

  override fun <T : Any> getState(component: Any?, componentName: String, stateClass: Class<T>, mergeInto: T?, reload: Boolean): T? {
    @Suppress("DEPRECATION", "UNCHECKED_CAST")
    when {
      stateClass === Element::class.java -> {
        val data = getXmlData(createSettingDescriptor(componentName))
        if (data != null) {
          return data as T
        }
        oldStorage?.getJdom(componentName)?.let {
          return it as T
        }
        return mergeInto
      }
      com.intellij.openapi.util.JDOMExternalizable::class.java.isAssignableFrom(stateClass) -> {
        // we don't care about data from the old storage for deprecated JDOMExternalizable
        val data = getXmlData(createSettingDescriptor(componentName)) ?: return mergeInto
        if (mergeInto != null) {
          thisLogger().error("State is ${stateClass.name}, merge into is $mergeInto, state element text is $data")
        }

        val t = MethodHandles.privateLookupIn(stateClass, MethodHandles.lookup())
          .findConstructor(stateClass, MethodType.methodType(Void.TYPE))
          .invoke() as com.intellij.openapi.util.JDOMExternalizable
        t.readExternal(data)
        return t as T
      }
      else -> {
        try {
          val beanBinding = bindingProducer.getRootBinding(stateClass) as NotNullDeserializeBinding
          if (beanBinding is KotlinxSerializationBinding) {
            val data = controller.getItem(createSettingDescriptor(componentName))
            if (data != null) {
              return cborFormat.decodeFromByteArray(beanBinding.serializer, data) as T
            }
            else {
              return oldStorage?.get(componentName)?.content?.let {
                beanBinding.decodeFromJson(it)
              } as T?
            }
          }
          else {
            return getXmlSerializationState(mergeInto = mergeInto, beanBinding = beanBinding, componentName = componentName)
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

  private fun <T : Any> getXmlSerializationState(mergeInto: T?, beanBinding: NotNullDeserializeBinding, componentName: String): T? {
    var result = mergeInto
    var hasData = false
    val bindings = (beanBinding as BeanBinding).bindings
    for ((index, binding) in bindings.withIndex()) {
      val data = getXmlData(createSettingDescriptor("$componentName.${binding.accessor.name}")) ?: continue
      if (result == null) {
        // create a result only if we have some data - do not return empty state class
        @Suppress("UNCHECKED_CAST")
        result = beanBinding.newInstance() as T
      }

      hasData = true
      BeanBinding.deserializeInto(result, data, null, bindings, index, index + 1)
    }

    if (!hasData && oldStorage != null) {
      val oldData = oldStorage.get(componentName)
      if (oldData != null) {
        @Suppress("UNCHECKED_CAST")
        result = mergeInto ?: beanBinding.newInstance() as T
        beanBinding.deserializeInto(result, oldData)
      }
    }
    return result
  }

  private fun getXmlData(key: SettingDescriptor<ByteArray>): Element? {
    try {
      return decodeCborToXml(controller.getItem(key) ?: return null)
    }
    catch (e: Throwable) {
      thisLogger().error("Cannot deserialize value for $key", e)
      return null
    }
  }

  override fun createSaveSessionProducer(): SaveSessionProducer {
    return ControllerBackedSaveSessionProducer(bindingProducer = bindingProducer, storageController = this)
  }

  override fun analyzeExternalChangesAndUpdateIfNeeded(componentNames: MutableSet<in String>) {
    // external change is not expected and not supported
  }

  internal fun createSettingDescriptor(key: String): SettingDescriptor<ByteArray> {
    return SettingDescriptor(
      key = key,
      pluginId = shimPluginId,
      tags = this.tags,
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

  override fun setState(component: Any?, componentName: String, state: Any?) {
    val settingDescriptor = storageController.createSettingDescriptor(componentName)
    if (state == null) {
      put(settingDescriptor, null)
      return
    }

    @Suppress("DEPRECATION")
    when (state) {
      is Element -> putJdomElement(settingDescriptor, state)
      is com.intellij.openapi.util.JDOMExternalizable -> {
        val element = Element(FileStorageCoreUtil.COMPONENT)
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
          for (binding in (beanBinding as BeanBinding).bindings) {
            if (state is SerializationFilter && !state.accepts(binding.accessor, state)) {
              continue
            }

            val element = beanBinding.serializePropertyInto(/* binding = */ binding,
                                                            /* o = */ state,
                                                            /* element = */ null,
                                                            /* filter = */ filter,
                                                            /* isFilterPropertyItself = */ true)
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
  private val cache: MutableMap<Class<*>, Binding> = HashMap()
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
    @Suppress("DuplicatedCode") var binding = createClassBinding(aClass, null, aClass)
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
