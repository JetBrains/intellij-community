// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment", "ReplaceGetOrSet")

package com.intellij.platform.settings.local

import com.intellij.configurationStore.SettingsSavingComponent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.ComponentManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.openapi.extensions.PluginId
import com.intellij.platform.settings.DelegatedSettingsController
import com.intellij.platform.settings.GetResult
import com.intellij.platform.settings.OldLocalValueSupplierTag
import com.intellij.platform.settings.SetResult
import com.intellij.platform.settings.SettingDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import java.nio.file.Files
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit
import kotlin.time.toDuration

internal class JsonMirrorController @JvmOverloads constructor(private val isProjectLevel: Boolean = false) : DelegatedSettingsController {
  init {
    if (!System.getProperty("idea.settings.json.mirror", "false").toBoolean()) {
      throw ExtensionNotApplicableException.create()
    }
  }

  private val service by lazy {
    service<JsonMirrorStorage>()
  }

  override fun <T : Any> getItem(key: SettingDescriptor<T>): GetResult<T?> {
    if (!isProjectLevel) {
      for (tag in key.tags) {
        if (tag is OldLocalValueSupplierTag) {
          // Element - save it to storage
          tag.value?.let {
            service.setItem(key, it)
          }
        }
      }
    }
    //println("${key.pluginId.idString}/${key.key}")
    return GetResult.inapplicable()
  }

  override fun <T : Any> setItem(key: SettingDescriptor<T>, value: T?): SetResult {
    if (!isProjectLevel && value is JsonElement) {
      service.setItem(key, value)
      //println(value.toString())
    }
    return SetResult.inapplicable()
  }

  override fun createChild(container: ComponentManager): DelegatedSettingsController {
    return JsonMirrorController(isProjectLevel = true)
  }
}

@Service
private class JsonMirrorStorage : SettingsSavingComponent {
  @OptIn(ExperimentalSerializationApi::class)
  private val jsonFormat = Json {
    prettyPrint = true
    prettyPrintIndent = "  "
  }

  // yes - save is ignored first 5 minutes
  private var lastSaved: Duration = Duration.ZERO

  private val storage = ConcurrentHashMap<PluginId, ConcurrentHashMap<String, JsonElement>>()

  fun setItem(key: SettingDescriptor<*>, value: JsonElement) {
    storage.computeIfAbsent(key.pluginId) { ConcurrentHashMap() }
      .put(key.key, value)
  }

  override suspend fun save() {
    val exitInProgress = ApplicationManager.getApplication().isExitInProgress
    val now = System.currentTimeMillis().toDuration(DurationUnit.MILLISECONDS)
    if (!exitInProgress && (now - lastSaved) < 30.seconds) {
      return
    }

    val keys = storage.keys.toMutableList()
    keys.sort()
    val jsonMap = LinkedHashMap<String, JsonElement>()
    for (key in keys) {
      val value = storage.get(key) ?: continue
      jsonMap.put(key.idString, transformToTree(value))
    }
    val data = jsonFormat.encodeToString(jsonMap)
    withContext(Dispatchers.IO) {
      Files.writeString(PathManager.getConfigDir().resolve("json-controller-state.json"), data)
    }

    lastSaved = now
  }
}

private fun transformToTree(map: Map<String, JsonElement>): JsonObject {
  val rootNode = LinkedHashMap<String, Any>()
  for ((compoundKey, value) in map) {
    val keyParts = compoundKey.split('.', limit = 2)
    var currentNode = rootNode
    for ((index, key) in keyParts.withIndex()) {
      if (index == keyParts.size - 1) {
        currentNode.put(key, value)
      }
      else {
        @Suppress("UNCHECKED_CAST")
        currentNode = currentNode.computeIfAbsent(key) { LinkedHashMap<String, Any>() } as LinkedHashMap<String, Any>
      }
    }
  }

  fun toJsonObject(map: MutableMap<String, Any>): JsonObject {
    map.replaceAll { _, v ->
      if (v is MutableMap<*, *>) {
        @Suppress("UNCHECKED_CAST")
        toJsonObject(v as MutableMap<String, Any>)
      }
      else {
        v
      }
    }

    @Suppress("UNCHECKED_CAST")
    return JsonObject(map as Map<String, JsonElement>)
  }

  return toJsonObject(rootNode)
}