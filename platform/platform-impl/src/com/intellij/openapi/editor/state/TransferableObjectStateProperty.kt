// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.state

import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer
import kotlin.reflect.KType

/**
 * An [ObjectStateProperty] that the Remote Development backend can send to the frontend.
 *
 * [customPropertySerializer] encodes the value. Without it, the kotlinx serializer of [type] does, so [type] must have
 * one. The backend sends the value when it has an override, and always when [alwaysTransfer] is set.
 */
class TransferableObjectStateProperty<T>(
  val type: KType,
  initialValue: T,
  defaultValueCalculator: SyncDefaultValueCalculator<T>,
  customOutValueModifier: CustomOutValueModifier<T>?,
  val customPropertySerializer: CustomPropertySerializer<T>?,
  val alwaysTransfer: Boolean = false,
) : ObjectStateProperty<T>(initialValue, defaultValueCalculator, customOutValueModifier), TransferableProperty<T> {
  private val defaultSerializer: KSerializer<Any?> by lazy { serializer(type) }

  override fun encodeToString(): String {
    if (customPropertySerializer != null) {
      return customPropertySerializer.encodeToString(value)
    }
    return commonFormat.encodeToString(defaultSerializer, value)
  }

  override fun decodeFromString(encoded: String): T {
    if (customPropertySerializer != null) {
      return customPropertySerializer.decodeFromString(encoded)
    }
    @Suppress("UNCHECKED_CAST")
    return commonFormat.decodeFromString(defaultSerializer, encoded) as T
  }
}
