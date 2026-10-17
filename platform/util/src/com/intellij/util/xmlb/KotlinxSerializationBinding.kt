// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.xmlb

import com.intellij.diagnostic.rethrowControlFlowException
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.util.xml.dom.XmlElement
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.serializer
import org.jdom.CDATA
import org.jdom.Element
import org.jdom.Text
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.VisibleForTesting
import java.util.Collections

@Volatile
private var json: Json = createJson()

/**
 * The [Json] instance of the settings serialization.
 * Do not keep it in a field: [clearKotlinxSerializationCache] replaces it.
 */
@Suppress("ObjectPropertyName")
@SettingsInternalApi
@get:Internal
val __json: Json
  get() = json

/**
 * Replaces [__json] with a new instance of the same configuration, and empties the schema cache of [Json.Default].
 * The new instance starts with an empty schema cache. Every plugin shares [Json.Default], and it cannot be replaced.
 * Its cache keeps the descriptor of each class that it decoded, so a plugin that unloads would stay reachable through it.
 */
@Internal
fun clearKotlinxSerializationCache() {
  json = createJson()
  try {
    schemaCacheOf(Json.Default).clear()
  }
  catch (e: Exception) {
    rethrowControlFlowException(e)
    fileLogger().warn("The schema cache of Json.Default is not where it was, so it keeps unloaded plugins", e)
  }
}

/**
 * The map of the schema cache of [json], keyed by descriptor. The cache is internal to kotlinx.serialization,
 * so this reads it by reflection. A change of the library internals fails here, and a test of the cleanup fails too.
 */
@Internal
@VisibleForTesting
@Throws(ReflectiveOperationException::class)
fun schemaCacheOf(json: Json): MutableMap<*, *> {
  val cache = Json::class.java.getDeclaredField("_schemaCache").apply { isAccessible = true }.get(json)
  @Suppress("UNCHECKED_CAST")
  return cache.javaClass.getDeclaredField("map").apply { isAccessible = true }.get(cache) as MutableMap<*, *>
}

private fun createJson(): Json {
  return Json {
    prettyPrint = true
    prettyPrintIndent = "  "
    ignoreUnknownKeys = true
  }
}

@SettingsInternalApi
@Internal
class KotlinxSerializationBinding(aClass: Class<*>) : Binding, RootBinding {
  private val serializer = serializer(aClass)

  override fun toJson(bean: Any, filter: SerializationFilter?): JsonElement {
    return __json.encodeToJsonElement(serializer, bean)
  }

  override fun fromJson(currentValue: Any?, element: JsonElement): Any? {
    return if (element == JsonNull) null else __json.decodeFromJsonElement(serializer, element)
  }

  override fun serialize(bean: Any, parent: Element, filter: SerializationFilter?) {
    val json = encodeToJson(bean)
    if (!json.isEmpty() && json != "{\n}") {
      parent.addContent(CDATA(json))
    }
  }

  override fun serialize(bean: Any, filter: SerializationFilter?): Element {
    val element = Element("state")
    val json = encodeToJson(bean)
    if (!json.isEmpty() && json != "{\n}") {
      element.addContent(CDATA(json))
    }
    return element
  }

  private fun encodeToJson(o: Any): String = __json.encodeToString(serializer, o)

  private fun decodeFromJson(data: String): Any = __json.decodeFromString(serializer, data)

  override fun <T : Any> isBoundTo(element: T, adapter: DomAdapter<T>): Boolean {
    throw UnsupportedOperationException("Only root object is supported")
  }

  override fun deserializeToJson(element: Element): JsonElement {
    val cdata = (element.content.firstOrNull() as? Text)?.value ?: return JsonObject(Collections.emptyMap())
    return __json.parseToJsonElement(cdata)
  }

  override fun <T : Any> deserialize(context: Any?, element: T, adapter: DomAdapter<T>): Any {
    val cdata = (if (element is Element) (element.content.firstOrNull() as? Text)?.text else (element as XmlElement).content) ?: return __json.decodeFromString(serializer, "{}")
    return decodeFromJson(cdata)
  }
}