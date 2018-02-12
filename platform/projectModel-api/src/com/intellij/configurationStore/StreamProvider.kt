/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.configurationStore

import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream
import org.jetbrains.annotations.TestOnly
import java.io.InputStream

interface StreamProvider {
  val enabled: Boolean
    get() = true

  /**
   * Called only on `write`
   */
  fun isApplicable(fileSpec: String, roamingType: RoamingType = RoamingType.DEFAULT) = true

  /**
   * @param fileSpec
   * @param content bytes of content, size of array is not actual size of data, you must use `size`
   * @param size actual size of data
   */
  fun write(fileSpec: String, content: ByteArray, size: Int = content.size, roamingType: RoamingType = RoamingType.DEFAULT)

  fun write(path: String, content: BufferExposingByteArrayOutputStream, roamingType: RoamingType = RoamingType.DEFAULT) = write(path, content.internalBuffer, content.size(), roamingType)

  /**
   * `true` if provider is applicable for file.
   */
  fun read(fileSpec: String, roamingType: RoamingType = RoamingType.DEFAULT, consumer: (InputStream?) -> Unit): Boolean

  /**
   * `true` if provider is fully responsible and local sources must be not used.
   */
  fun processChildren(path: String, roamingType: RoamingType, filter: (name: String) -> Boolean, processor: (name: String, input: InputStream, readOnly: Boolean) -> Boolean): Boolean

  /**
   * Delete file or directory
   *
   * `true` if provider is fully responsible and local sources must be not used.
   */
  fun delete(fileSpec: String, roamingType: RoamingType = RoamingType.DEFAULT): Boolean
}

@TestOnly
fun StreamProvider.write(path: String, content: String) {
  write(path, content.toByteArray())
}