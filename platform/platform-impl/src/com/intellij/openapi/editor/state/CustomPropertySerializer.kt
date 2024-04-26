// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.state

interface CustomPropertySerializer<T> {

  /**
   * @throws kotlinx.serialization.SerializationException
   * @throws IllegalArgumentException
   */
  fun encodeToString(obj: T): String

  /**
   * @throws kotlinx.serialization.SerializationException
   * @throws IllegalArgumentException
   */
  fun decodeFromString(encoded: String): T

}
