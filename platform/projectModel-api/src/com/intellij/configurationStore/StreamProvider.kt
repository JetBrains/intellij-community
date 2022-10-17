// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.configurationStore

import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import java.io.InputStream

@ApiStatus.Internal
interface StreamProvider {
  /**
   * Whether is enabled.
   */
  val enabled: Boolean
    get() = true

  /**
   * Whether is exclusive and cannot be used alongside another provider.
   *
   * Doesn't imply [enabled], callers should check [enabled] also if needed.
   */
  val isExclusive: Boolean

  /**
   * Called only on `write`
   */
  fun isApplicable(fileSpec: String, roamingType: RoamingType = RoamingType.DEFAULT): Boolean = true

  fun write(fileSpec: String, content: ByteArray, roamingType: RoamingType = RoamingType.DEFAULT)

  @Deprecated("Use #write(fileSpec, content, roamingType) without the 'size' parameter")
  fun write(fileSpec: String, content: ByteArray, size: Int, roamingType: RoamingType = RoamingType.DEFAULT) : Unit =
    write(fileSpec, content, roamingType)

  @Deprecated("Use #write(fileSpec, content, roamingType) with ByteArray parameter")
  fun write(path: String, content: BufferExposingByteArrayOutputStream, roamingType: RoamingType = RoamingType.DEFAULT): Unit =
    write(path, content.toByteArray(), roamingType)


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