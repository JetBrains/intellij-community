// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.state

import com.intellij.diagnostic.rethrowControlFlowException
import kotlinx.serialization.KSerializer
import kotlinx.serialization.StringFormat
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import kotlinx.serialization.serializerOrNull
import java.lang.reflect.Modifier
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass
import kotlin.reflect.KType

/**
 * An [ObjectStateProperty] that the Remote Development backend can send to the frontend.
 *
 * [customPropertySerializer] encodes the value. Without it, the kotlinx serializer of [type] does, so [type] must have
 * one. The backend sends the value when it has an override, and always when [alwaysTransfer] is set.
 */
class TransferableObjectStateProperty<T> internal constructor(
  state: ObservableState,
  index: Int,
  val type: KType,
  initialValue: T,
  defaultValueCalculator: SyncDefaultValueCalculator<T>,
  customOutValueModifier: CustomOutValueModifier<T>?,
  val customPropertySerializer: CustomPropertySerializer<T>?,
  val alwaysTransfer: Boolean,
) : ObjectStateProperty<T>(state, index, initialValue, defaultValueCalculator, customOutValueModifier),
    TransferableProperty<T> {
  private val defaultSerializer: KSerializer<Any?> by lazy { serializer(type) }

  override fun encodeToString(): String {
    if (customPropertySerializer != null) {
      return customPropertySerializer.encodeToString(value)
    }
    return COMMON_FORMAT.encodeToString(defaultSerializer, value)
  }

  override fun decodeFromString(encoded: String): T {
    if (customPropertySerializer != null) {
      return customPropertySerializer.decodeFromString(encoded)
    }
    // Safe: [type] is the type of T, so its serializer decodes a T.
    @Suppress("UNCHECKED_CAST")
    return COMMON_FORMAT.decodeFromString(defaultSerializer, encoded) as T
  }

  internal companion object {
    /**
     * The format of a property without a custom serializer.
     */
    private val COMMON_FORMAT: StringFormat = Json { allowSpecialFloatingPointValues = true }

    private val DEFAULT_SERIALIZER_PRESENCE: ConcurrentHashMap<KType, Boolean> = ConcurrentHashMap()

    /**
     * `kotlin.collections.List` has no runtime class of its own: it is a mapped type erased to [java.util.List],
     * so the raw Java interface is referenced here explicitly.
     */
    @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
    private val JAVA_LIST_CLASS: Class<*> = java.util.List::class.java

    /**
     * Whether kotlinx can serialize a value of [type] without a custom serializer. The answer is cached per type.
     */
    fun hasDefaultSerializer(type: KType): Boolean {
      if (isNotRecommendedForSerialization(type)) {
        return false
      }
      return DEFAULT_SERIALIZER_PRESENCE.computeIfAbsent(type) {
        try {
          serializerOrNull(type) != null
        } catch (e: Exception) {
          rethrowControlFlowException(e)
          false
        }
      }
    }

    private fun isNotRecommendedForSerialization(type: KType): Boolean {
      val classifier = type.classifier
      if (classifier !is KClass<*>) {
        return true
      }
      // The plain java.lang.Class API is used on purpose. kotlin-reflect members like `isSubclassOf` and `isAbstract`
      // deserialize the whole class metadata, and that can freeze the EDT (IJPL-251839).
      val javaClass = classifier.java
      // A list works, and a CharSequence does not.
      if (JAVA_LIST_CLASS.isAssignableFrom(javaClass)) {
        return false
      }
      // The JVM marks primitives, arrays, and enums with constant-specific bodies as abstract, but they are final in Kotlin.
      if (javaClass.isPrimitive || javaClass.isArray || javaClass.isEnum) {
        return false
      }
      return Modifier.isAbstract(javaClass.modifiers)
    }
  }
}
