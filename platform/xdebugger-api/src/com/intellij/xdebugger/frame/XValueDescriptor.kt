// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.frame

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.ExtensionPointAdapter
import com.intellij.openapi.extensions.ExtensionPointName
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.modules.PolymorphicModuleBuilder
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import org.jetbrains.annotations.ApiStatus

/**
 * Provides additional information about XValue which can be used by UI and actions on the Frontend.
 *
 * Internal plugins may extend this interface providing custom implementation, but they should be serializable.
 * Also, it is required to provide serializer for the implementation using
 * [com.intellij.xdebugger.frame.XValueCustomDescriptorSerializerProvider].
 * Otherwise, the default implementation will be used during a serialization/deserialization process.
 *
 *
 * @see createXValueDescriptor
 * @see XValueCustomDescriptorSerializerProvider
 */
@ApiStatus.Internal
@Serializable(with = XValueDescriptorSerializer::class)
interface XValueDescriptor {
  /**
   * [kind] is used to differentiate various implementations of XValue by their type instead of using `instanceOf` on [XValue].
   * Examples of possible kinds: "JavaValue", "PhpValue", "RubyValue"
   *
   * Implementation detail: since the frontend uses [com.intellij.platform.debugger.impl.frontend.evaluate.quick.FrontendXValue],
   * which makes `instanceOf` comparisons of XValue impossible, this property provides a way to
   * differentiate between various types of XValue on the frontend.
   */
  val kind: String
}

// TODO: should be called just XValueDescriptor, but it won't be available in Java then
@ApiStatus.Internal
fun createXValueDescriptor(kind: String): XValueDescriptor {
  return XValueDescriptorImpl(kind)
}

@ApiStatus.Internal
interface XValueCustomDescriptorSerializerProvider {
  companion object {
    internal val EP_NAME = ExtensionPointName<XValueCustomDescriptorSerializerProvider>("com.intellij.xdebugger.xValueCustomDescriptorSerializerProvider")
  }

  fun registerSerializer(builder: PolymorphicModuleBuilder<XValueDescriptor>)
}

@Serializable
private data class XValueDescriptorImpl(override val kind: String) : XValueDescriptor


private object XValueDescriptorSerializer : KSerializer<XValueDescriptor> {
  override val descriptor: SerialDescriptor = JsonElement.serializer().descriptor

  override fun serialize(encoder: Encoder, value: XValueDescriptor) {
    val json = service<XValueDescriptorJsonProvider>().json
    val element = json.encodeToJsonElement(value)
    encoder.encodeSerializableValue(JsonElement.serializer(), element)
  }

  override fun deserialize(decoder: Decoder): XValueDescriptor {
    val json = service<XValueDescriptorJsonProvider>().json
    val element = decoder.decodeSerializableValue(JsonElement.serializer())
    return json.decodeFromJsonElement(element)
  }
}

@Service
private class XValueDescriptorJsonProvider(cs: CoroutineScope) {
  @Volatile
  var json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
  }

  init {
    updateJson()
    XValueCustomDescriptorSerializerProvider.EP_NAME.addExtensionPointListener(cs, object : ExtensionPointAdapter<XValueCustomDescriptorSerializerProvider>() {
      override fun extensionListChanged() {
        updateJson()
      }
    })
  }

  private fun updateJson() {
    json = Json {
      ignoreUnknownKeys = true
      encodeDefaults = true
      serializersModule = SerializersModule {
        polymorphic(XValueDescriptor::class) {
          for (provider in XValueCustomDescriptorSerializerProvider.EP_NAME.extensionList) {
            provider.registerSerializer(this)
          }
          defaultDeserializer {
            XValueDescriptorImpl.serializer()
          }
        }
      }
    }
  }
}