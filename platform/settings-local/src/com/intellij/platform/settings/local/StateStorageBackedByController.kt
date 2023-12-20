// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment", "ReplaceGetOrSet")

package com.intellij.platform.settings.local

import com.intellij.configurationStore.SaveSession
import com.intellij.configurationStore.SaveSessionProducer
import com.intellij.configurationStore.jdomSerializer
import com.intellij.openapi.components.StateStorage
import com.intellij.openapi.components.impl.stores.FileStorageCoreUtil
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.JDOMUtil
import com.intellij.platform.settings.SettingDescriptor
import com.intellij.platform.settings.SettingTag
import com.intellij.platform.settings.StringSettingSerializerDescriptor
import com.intellij.platform.settings.settingDescriptor
import com.intellij.serialization.SerializationException
import com.intellij.serialization.xml.KotlinAwareBeanBinding
import com.intellij.serialization.xml.KotlinxSerializationBinding
import com.intellij.util.xml.dom.readXmlAsModel
import com.intellij.util.xmlb.*
import kotlinx.serialization.Serializable
import org.jdom.Element
import java.io.StringReader
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
) : StateStorage {
  private val bindingProducer = BindingProducer()

  override fun <T : Any> getState(component: Any?, componentName: String, stateClass: Class<T>, mergeInto: T?, reload: Boolean): T? {
    @Suppress("DEPRECATION", "UNCHECKED_CAST")
    return when {
      stateClass === Element::class.java -> {
        val data = controller.getItem(createSettingDescriptor(componentName)) ?: return mergeInto
        JDOMUtil.load(data) as T
      }
      com.intellij.openapi.util.JDOMExternalizable::class.java.isAssignableFrom(stateClass) -> {
        val data = controller.getItem(createSettingDescriptor(componentName)) ?: return mergeInto
        if (mergeInto != null) {
          thisLogger().error("State is ${stateClass.name}, merge into is $mergeInto, state element text is $data")
        }

        val t = MethodHandles.privateLookupIn(stateClass, MethodHandles.lookup())
          .findConstructor(stateClass, MethodType.methodType(Void.TYPE))
          .invoke() as com.intellij.openapi.util.JDOMExternalizable
        t.readExternal(JDOMUtil.load(data))
        t as T
      }
      else -> {
        try {
          val beanBinding = bindingProducer.getRootBinding(stateClass) as NotNullDeserializeBinding
          if (beanBinding is KotlinxSerializationBinding) {
            val data = controller.getItem(createSettingDescriptor(componentName)) ?: return mergeInto
            return beanBinding.decodeFromJson(data) as T
          }
          else {
            var result = mergeInto
            val bindings = (beanBinding as BeanBinding).bindings
            for ((index, binding) in bindings.withIndex()) {
              val data = controller.getItem(createSettingDescriptor("$componentName.${binding.accessor.name}")) ?: continue
              if (result == null) {
                // create a result only if we have some data - do not return empty state class
                result = beanBinding.newInstance() as T
              }
              BeanBinding.deserializeInto(result, readXmlAsModel(StringReader(data)), bindings, index, index + 1)
            }
            return result
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

  override fun hasState(componentName: String, reloadData: Boolean): Boolean {
    return controller.hasKeyStartsWith("$shimPluginId.$componentName.")
  }

  override fun createSaveSessionProducer(): SaveSessionProducer {
    return ControllerBackedSaveSessionProducer(bindingProducer = bindingProducer, storageController = this)
  }

  override fun analyzeExternalChangesAndUpdateIfNeeded(componentNames: MutableSet<in String>) {
    // external change is not expected and not supported
  }

  internal fun createSettingDescriptor(key: String): SettingDescriptor<String> {
    val tags = tags
    return settingDescriptor(key = key, pluginId = shimPluginId, serializer = StringSettingSerializerDescriptor) {
      this.tags = tags
    }
  }
}

private class ControllerBackedSaveSessionProducer(
  private val bindingProducer: BindingProducer,
  private val storageController: StateStorageBackedByController,
) : SaveSessionProducer {
  private fun put(key: SettingDescriptor<String>, value: String?) {
    storageController.controller.putIfDiffers(key, value)
  }

  override fun setState(component: Any?, componentName: String, state: Any?) {
    val settingDescriptor = storageController.createSettingDescriptor(componentName)
    if (state == null) {
      put(settingDescriptor, null)
      return
    }

    @Suppress("DEPRECATION")
    when (state) {
      is Element -> put(settingDescriptor, jdomElementToString(state))
      is com.intellij.openapi.util.JDOMExternalizable -> {
        val element = Element(FileStorageCoreUtil.COMPONENT)
        state.writeExternal(element)
        put(settingDescriptor, jdomElementToString(element))
      }
      else -> {
        val aClass = state.javaClass
        val beanBinding = bindingProducer.getRootBinding(aClass)
        if (beanBinding is KotlinxSerializationBinding) {
          // `Serializable` is not intercepted - it is not used for regular settings that we want to support on a property level
          put(settingDescriptor, beanBinding.encodeToJson(state))
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
            put(settingDescriptor.withSubName(binding.accessor.name), element?.let { jdomElementToString(it) })
          }
        }
      }
    }
  }

  override fun createSaveSession(): SaveSession? = null
}

private fun jdomElementToString(state: Element): String? = if (state.isEmpty) null else JDOMUtil.writeElement(state)

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
    assert(aClass === originalType)
    return getRootBinding(aClass)
  }

  fun createRootBinding(aClass: Class<*>): Binding {
    var binding = createClassBinding(aClass, null, aClass)
    if (binding == null) {
      assert(!aClass.isAnnotationPresent(Serializable::class.java))
      if (aClass.isAnnotationPresent(Serializable::class.java)) {
        binding = KotlinxSerializationBinding(aClass)
      }
      else {
        binding = KotlinAwareBeanBinding(aClass)
      }
    }
    cache.put(aClass, binding)
    try {
      binding.init(aClass, this)
    }
    catch (e: Throwable) {
      cache.remove(aClass)
      throw e
    }
    return binding
  }
}
