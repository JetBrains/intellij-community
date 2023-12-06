// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.state

import kotlinx.serialization.serializer
import kotlin.reflect.KType

// todo doc (conditions, requirements)
class TransferableObjectStateProperty<T>(val type: KType,
                                         initialValue: T,
                                         defaultValueCalculator: SyncDefaultValueCalculator<T>,
                                         customOutValueModifier: CustomOutValueModifier<T>?,
                                         val customPropertySerializer: CustomPropertySerializer<T>?)
  : ObjectStateProperty<T>(initialValue, defaultValueCalculator, customOutValueModifier), TransferableProperty<T> {
  override fun encodeToString(): String {
    return if (customPropertySerializer != null) customPropertySerializer.encodeToString(value)
    else commonFormat.encodeToString(serializer(type), value)
  }

  override fun decodeFromString(encoded: String): T {
    @Suppress("UNCHECKED_CAST")
    return if (customPropertySerializer != null) customPropertySerializer.decodeFromString(encoded)
    else commonFormat.decodeFromString(serializer(type), encoded) as T
  }
}