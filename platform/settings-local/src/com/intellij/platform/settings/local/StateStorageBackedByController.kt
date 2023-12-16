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
import com.intellij.platform.settings.*
import com.intellij.serialization.xml.KotlinAwareBeanBinding
import com.intellij.serialization.xml.KotlinxSerializationBinding
import com.intellij.util.xml.dom.readXmlAsModel
import com.intellij.util.xmlb.BeanBinding
import com.intellij.util.xmlb.Binding
import com.intellij.util.xmlb.SerializationFilter
import com.intellij.util.xmlb.XmlSerializerImpl
import kotlinx.serialization.Serializable
import org.jdom.Element
import java.io.StringReader
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import java.lang.reflect.Type
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

private val shimPluginId = PluginId.getId("__controller_shim__")

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

internal class StateStorageBackedByController(private val controller: ChainedSettingsController) : StateStorage {
  private val bindingProducer = BindingProducer()

  override fun <T : Any> getState(component: Any?, componentName: String, stateClass: Class<T>, mergeInto: T?, reload: Boolean): T? {
    val data = controller.getItem(createSettingDescriptor(componentName), emptyList())
    @Suppress("DEPRECATION", "UNCHECKED_CAST")
    return when {
      data == null -> mergeInto
      stateClass == Element::class.java -> JDOMUtil.load(data) as T
      com.intellij.openapi.util.JDOMExternalizable::class.java.isAssignableFrom(stateClass) -> {
        if (mergeInto != null) {
          thisLogger().error("State is ${stateClass.name}, merge into is $mergeInto, state element text is $data")
        }

        val t = MethodHandles.privateLookupIn(stateClass, MethodHandles.lookup())
          .findConstructor(stateClass, MethodType.methodType(Void.TYPE))
          .invoke() as com.intellij.openapi.util.JDOMExternalizable
        t.readExternal(JDOMUtil.load(data))
        t as T
      }
      mergeInto == null -> {
        jdomSerializer.deserialize(readXmlAsModel(StringReader(data)), stateClass)
      }
      else -> {
        jdomSerializer.deserializeInto(mergeInto, readXmlAsModel(StringReader(data)))
        mergeInto
      }
    }
  }

  override fun hasState(componentName: String, reloadData: Boolean): Boolean {
    return controller.getItem(createSettingDescriptor(componentName), emptyList()) != null
  }

  private fun createSettingDescriptor(componentName: String): SettingDescriptor<String> {
    return settingDescriptor(componentName, shimPluginId, StringSettingSerializerDescriptor) {
      tags = listOf(CacheTag)
    }
  }

  override fun createSaveSessionProducer(): SaveSessionProducer {
    // save is implemented in another way
    return object : SaveSessionProducer {
      private val map = ConcurrentLinkedQueue<Pair<SettingDescriptor<String>, String?>>()

      override fun setState(component: Any?, componentName: String, state: Any?) {
        val settingDescriptor = createSettingDescriptor(componentName)
        if (state == null) {
          map.add(settingDescriptor to null)
          return
        }

        @Suppress("DEPRECATION")
        when (state) {
          is Element -> map.add(settingDescriptor to jdomElementToString(state))
          is com.intellij.openapi.util.JDOMExternalizable -> {
            val element = Element(FileStorageCoreUtil.COMPONENT)
            state.writeExternal(element)
            map.add(settingDescriptor to jdomElementToString(element))
          }
          else -> {
            val aClass = state.javaClass
            val beanBinding = bindingProducer.getRootBinding(aClass)
            if (beanBinding is KotlinxSerializationBinding) {
              // `Serializable` is not intercepted - it is not used for regular settings that we want to support on a property level
              map.add(settingDescriptor to beanBinding.encodeToJson(state))
            }
            else {
              val filter = jdomSerializer.getDefaultSerializationFilter()
              for (binding in (beanBinding as BeanBinding).bindings) {
                if (state is SerializationFilter && !state.accepts(binding.accessor, state)) {
                  continue
                }

                val element = beanBinding.serializePropertyInto(binding, state, null, filter, true)
                map.add(settingDescriptor.withSubName(binding.accessor.name) to element?.let { jdomElementToString(it) })
              }
            }
          }
        }
      }

      override fun createSaveSession(): SaveSession? {
        if (map.isEmpty()) {
          return null
        }

        return object : SaveSession {
          override suspend fun save() {
            for ((key, data) in map) {
              if (data == null) {
                controller.setItem(key, null, emptyList())
              }
              else {
                controller.setItem(key = key, value = data, chain = emptyList())
              }
            }
          }

          // used only if VFS is required - never for our case
          override fun saveBlocking() = throw UnsupportedOperationException()
        }
      }
    }
  }

  override fun analyzeExternalChangesAndUpdateIfNeeded(componentNames: MutableSet<in String>) {
    // external change is not expected and not supported
  }
}

private fun jdomElementToString(state: Element): String? = if (state.isEmpty) null else JDOMUtil.writeElement(state)