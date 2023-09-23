// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.state

import kotlinx.serialization.serializer
import kotlin.reflect.KType

// todo doc (conditions, requirements)
class TransferableObjectStateProperty<T>(val type: KType,
                                         initialValue: T,
                                         defaultValueCalculator: SyncDefaultValueCalculator<T>,
                                         customOutValueModifier: CustomOutValueModifier<T>? = null)
  : ObjectStateProperty<T>(initialValue, defaultValueCalculator, customOutValueModifier), TransferableProperty<T> {
  override fun encodeToString(): String {
    return commonFormat.encodeToString(serializer(type), value)
  }

  override fun decodeFromString(encoded: String): T {
    @Suppress("UNCHECKED_CAST")
    return commonFormat.decodeFromString(serializer(type), encoded) as T
  }
}