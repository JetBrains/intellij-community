// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.state

import kotlinx.serialization.SerializationException

interface TransferableProperty<T> {

  /**
   * @throws SerializationException
   * @throws IllegalArgumentException
   */
  fun encodeToString(): String

  /**
   * @throws SerializationException
   * @throws IllegalArgumentException
   */
  fun decodeFromString(encoded: String): T
}