// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.components

import com.google.gson.GsonBuilder
import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.*
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.serialization.MutableAccessor
import com.intellij.serviceContainer.ComponentManagerImpl
import com.intellij.util.xmlb.BeanBinding
import java.util.*
import java.util.function.IntPredicate


internal fun buildSettingsModel(): SettingsModel {
  val descriptors = mutableListOf<ComponentDescriptor>()
  val componentManager = ApplicationManager.getApplication() as ComponentManagerImpl
  componentManager.processAllImplementationClasses { aClass, descriptor ->
    if (PersistentStateComponent::class.java.isAssignableFrom(aClass)) {
      val state = aClass.getAnnotation(State::class.java)
      @Suppress("UNCHECKED_CAST")
      descriptors.add(
        ComponentDescriptor(
          descriptor?.name?.toString() ?: "",
          aClass as Class<PersistentStateComponent<*>>,
          descriptor,
          state
        )
      )
    }
  }
  descriptors.sortWith(
    compareBy<ComponentDescriptor> { it.name }.thenBy { it.aClass.name }
  )
  return SettingsModel(descriptors)
}

internal class SettingsModel(val descriptors: List<ComponentDescriptor>) {

  fun toJson(): String {
    val builder = GsonBuilder()
    builder.setPrettyPrinting()
    builder.registerTypeAdapter(ComponentDescriptor::class.java, object : TypeAdapter<ComponentDescriptor>() {
      override fun write(out: JsonWriter?, value: ComponentDescriptor?) {
        out?.let {
          value?.let { descriptor ->
            descriptor.state?.name?.let { stateName ->
              out.beginObject()
              out.name("name")
              out.value(stateName)
              descriptor.pluginDescriptor?.pluginId?.let {
                out.name("pluginId")
                out.value(it.idString)
              }
              out.name("class")
              out.value(descriptor.aClass.name)
              findStorage(descriptor.state)?.let { storage ->
                out.name("storage")
                out.value(storage.value)
              }
              @Suppress("IncorrectServiceRetrieving")
              ApplicationManager.getApplication().getService(descriptor.aClass)?.let { component ->
                component.state?.let { state ->
                  val infoList = collectFieldInfo(state)
                  if (!infoList.isEmpty()) {
                    out.name("properties")
                    out.beginArray()
                    infoList.forEach { info ->
                      out.beginObject()
                      out.name("name")
                      out.value(info.jsonName)
                      if (info.name != info.jsonName) {
                        out.name("mapTo")
                        out.value(info.name)
                      }
                      out.name("type")
                      out.value(info.typeName)
                      if (info.variants.isNotEmpty()) {
                        out.name("variants")
                        out.beginArray()
                        info.variants.forEach {
                          out.beginObject()
                          out.name("value")
                          out.value(it.value)
                          if (it.value != it.mapTo) {
                            out.name("mapTo")
                            out.value(it.mapTo)
                          }
                          out.endObject()
                        }
                        out.endArray()
                      }
                      out.endObject()
                    }
                    out.endArray()
                  }
                }
              }
              it.endObject()
            }
          }
        }
      }

      override fun read(inReader: JsonReader?): ComponentDescriptor {
        throw UnsupportedOperationException()
      }

    })
    val gson = builder.create()
    return gson.toJson(descriptors)
  }

  private fun findStorage(state: State): Storage? {
    state.storages.forEach { storage ->
      if (!storage.deprecated) {
        return storage
      }
    }
    return null
  }

  private fun collectFieldInfo(state: Any): List<FieldInfo> {
    val infoList = mutableListOf<FieldInfo>()
    val accessors = BeanBinding.getAccessors(state::class.java)
    accessors.forEach{
      infoList += FieldInfo(it.name, allUpperCaseToLowerCase(it.name), convertTypeName(it), getVariants(it.valueClass))
    }
    return infoList
  }


  private fun convertTypeName(accessor: MutableAccessor): String {
    val valueClass = accessor.valueClass
    val original = valueClass.typeName
    if (valueClass.isEnum) return "enum"
    when (original) {
      "java.lang.Integer" -> return "int"
      "java.lang.String" -> return "string"
      "java.lang.Boolean" -> return "boolean"
    }
    when (accessor.genericType.typeName) {
      "java.util.List<java.lang.String>" -> return "string-list"
      "java.util.Set<java.lang.String>" -> return "string-set"
      "java.util.Collection<java.lang.String>" -> return "strings"
    }
    return original
  }

  private fun getVariants(valueClass: Class<*>): List<VariantInfo> {
    if (valueClass.isEnum) {
      return valueClass.enumConstants.map { VariantInfo(getVariantString(it.toString()), it.toString()) }
    }
    return emptyList()
  }

  data class VariantInfo(
    val value: String,
    val mapTo: String
  )

  private fun allUpperCaseToLowerCase(name: String): String =
    if (name.chars().allMatch(IntPredicate {
        Character.isUpperCase(it) || !Character.isAlphabetic(it)
      })) {
      name.lowercase(Locale.ENGLISH)
    }
    else name

  private data class FieldInfo(
    val name: String,
    val jsonName: String,
    val typeName: String,
    val variants: List<VariantInfo> = emptyList()
  )

  private fun getVariantString(original: String): String {
    val buf = StringBuilder()
    original.forEach { c->
      if (Character.isLetter(c) || Character.isDigit(c) || c == '_' || c == '-') {
        buf.append(c)
      }
      else if (c == ' ') {
        buf.append('_')
      }
    }
    return buf.toString().lowercase()
  }
}

data class ComponentDescriptor(
  val name: String,
  val aClass: Class<PersistentStateComponent<*>>,
  val pluginDescriptor: PluginDescriptor?,
  val state: State?
) {
  fun getRoamingTypeString(): String {
    if (state != null) {
      var roamingType: String? = null
      state.storages.forEach {
        if (!it.deprecated) {
          val storageRoamingType =
            if (it.value == StoragePathMacros.NON_ROAMABLE_FILE ||
                it.value == StoragePathMacros.CACHE_FILE ||
                it.value == StoragePathMacros.WORKSPACE_FILE) "DISABLED"
            else it.roamingType.toString()
          if (roamingType == null) {
            roamingType = storageRoamingType
          }
          else {
            if (roamingType != storageRoamingType) {
              roamingType = "MIXED"
            }
          }
        }
      }
      return roamingType ?: ""
    }
    return ""
  }

  fun getCategoryString(): String {
    val roamingType = getRoamingTypeString()
    if (roamingType != RoamingType.DISABLED.toString() && pluginDescriptor != null) {
      if (pluginDescriptor.name == PluginManagerCore.SPECIAL_IDEA_PLUGIN_ID.idString) {
        return state?.category?.name ?: ""
      }
      else {
        return ComponentCategorizer.getPluginCategory(aClass, pluginDescriptor).toString()
      }
    }
    else {
      return ""
    }
  }
}