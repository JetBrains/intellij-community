// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.xmlb

import com.intellij.util.xml.dom.XmlElement
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import org.jdom.CDATA
import org.jdom.Element
import org.jdom.Text
import org.jetbrains.annotations.ApiStatus.Internal
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import java.util.*

@Suppress("ObjectPropertyName")
@OptIn(ExperimentalSerializationApi::class)
@SettingsInternalApi
@Internal
val __json: Json = Json {
  prettyPrint = true
  prettyPrintIndent = "  "
  ignoreUnknownKeys = true
}

private val lookup = MethodHandles.lookup()
private val kotlinMethodType = MethodType.methodType(KSerializer::class.java)

@SettingsInternalApi
@Internal
class KotlinxSerializationBinding(aClass: Class<*>) : Binding, RootBinding {
  private val serializer: KSerializer<Any>

  init {
    // use `in` as it fixes memory leak - JDK impl hold reference to a created method handle, and we cannot unload the plugin
    val lookup = lookup.`in`(aClass)
    val findStaticGetter = lookup.findStaticGetter(aClass, "Companion", aClass.classLoader.loadClass(aClass.name + "\$Companion"))
    val companion = findStaticGetter.invoke()
    @Suppress("UNCHECKED_CAST")
    serializer = lookup.findVirtual(companion.javaClass, "serializer", kotlinMethodType).invoke(companion) as KSerializer<Any>
  }

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