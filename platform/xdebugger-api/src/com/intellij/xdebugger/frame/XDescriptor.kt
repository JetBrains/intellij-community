// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.frame

import com.intellij.openapi.extensions.ExtensionPointName
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.annotations.ApiStatus

/**
 * Provides additional information about a platform entity (e.g., [XValue], [XStackFrame], [XExecutionStack] ...)
 * which can be used by UI and actions on the Frontend.
 *
 * Internal plugins may extend this interface providing custom implementation, but they should be serializable.
 * Also, it is required to provide serializer for the implementation using
 * [com.intellij.xdebugger.frame.CustomXDescriptorSerializerProvider].
 * Otherwise, the default implementation will be used during a serialization/deserialization process.
 *
 * @see CustomXDescriptorSerializerProvider
 */
@ApiStatus.Internal
@Serializable(with = XDescriptorSerializer::class)
interface XDescriptor {
  /**
   * [kind] is used to differentiate various implementations of platform entities by their type instead of using `instanceOf`.
   * For [XValue] examples of possible kinds may be: "JavaValue", "PhpValue", "RubyValue";
   * Or alternatively for [XExecutionStack]: "JavaExecutionStack", "PhpExecutionStack".
   *
   * Implementation detail: since the frontend uses [com.intellij.platform.debugger.impl.frontend.evaluate.quick.FrontendXValue],
   * which makes `instanceOf` comparisons of [XValue] impossible, this property provides a way to
   * differentiate between various types of [XValue] on the frontend.
   */
  val kind: String
}

@ApiStatus.Internal
interface CustomXDescriptorSerializerProvider {
  companion object {
    internal val EP_NAME = ExtensionPointName<CustomXDescriptorSerializerProvider>("com.intellij.xdebugger.customXDescriptorSerializerProvider")
  }

  fun getSerializer(kind: String): KSerializer<out XDescriptor>?
}

@Serializable
private data class XDescriptorImpl(override val kind: String) : XDescriptor

private object XDescriptorSerializer : KSerializer<XDescriptor> {
  private val json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
  }

  override val descriptor: SerialDescriptor = JsonElement.serializer().descriptor

  override fun serialize(encoder: Encoder, value: XDescriptor) {
    val serializer = getSerializerByKind(value.kind)
    val element = json.encodeToJsonElement(serializer as KSerializer<XDescriptor>, value)
    encoder.encodeSerializableValue(JsonElement.serializer(), element)
  }

  override fun deserialize(decoder: Decoder): XDescriptor {
    val element = decoder.decodeSerializableValue(JsonElement.serializer())
    if (!element.jsonObject.containsKey("kind")) {
      throw IllegalArgumentException("Missing required 'kind' property")
    }
    val kind = element.jsonObject["kind"]!!.jsonPrimitive.content
    val serializer = getSerializerByKind(kind)
    return json.decodeFromJsonElement(serializer, element)
  }

  private fun getSerializerByKind(kind: String): KSerializer<out XDescriptor> {
    return CustomXDescriptorSerializerProvider.EP_NAME.extensionList.firstNotNullOfOrNull { it.getSerializer(kind) }
           ?: XDescriptorImpl.serializer()
  }
}