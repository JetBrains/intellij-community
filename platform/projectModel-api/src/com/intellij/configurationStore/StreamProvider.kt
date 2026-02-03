// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.configurationStore

import com.intellij.openapi.components.RoamingType
import org.jetbrains.annotations.ApiStatus.Internal
import java.io.InputStream

@Internal
interface StreamProvider {
  /**
   * Whether it is enabled.
   */
  val enabled: Boolean
    get() = true

  /**
   * Whether it is exclusive and cannot be used alongside another provider.
   *
   * Doesn't imply [enabled], callers should check [enabled] also if needed.
   */
  val isExclusive: Boolean

  val saveStorageDataOnReload: Boolean
    get() = true

  /**
   * Called only on `write`
   */
  fun isApplicable(fileSpec: String, roamingType: RoamingType = RoamingType.DEFAULT): Boolean = true

  fun write(fileSpec: String, content: ByteArray, roamingType: RoamingType = RoamingType.DEFAULT)

  /**
   * `true` if provider is applicable for file.
   */
  fun read(fileSpec: String, roamingType: RoamingType = RoamingType.DEFAULT, consumer: (InputStream?) -> Unit): Boolean

  /**
   * `true` if provider is fully responsible and local sources must be not used.
   */
  fun processChildren(path: String,
                      roamingType: RoamingType,
                      filter: (name: String) -> Boolean,
                      processor: (name: String, input: InputStream, readOnly: Boolean) -> Boolean): Boolean

  /**
   * Delete file or directory
   *
   * `true` if provider is fully responsible and local sources must be not used.
   */
  fun delete(fileSpec: String, roamingType: RoamingType = RoamingType.DEFAULT): Boolean

  /**
   * Check whether the file shouldn't be stored anymore and delete it if it shouldn't.
   */
  fun deleteIfObsolete(fileSpec: String, roamingType: RoamingType) {
  }

  fun getInstanceOf(aClass: Class<out StreamProvider>): StreamProvider = throw UnsupportedOperationException()
}

@Internal
object DummyStreamProvider : StreamProvider {
  override val isExclusive: Boolean
    get() = true

  override fun write(fileSpec: String, content: ByteArray, roamingType: RoamingType) {
  }

  override fun read(fileSpec: String, roamingType: RoamingType, consumer: (InputStream?) -> Unit): Boolean {
    return false
  }

  override fun processChildren(
    path: String,
    roamingType: RoamingType,
    filter: (name: String) -> Boolean,
    processor: (name: String, input: InputStream, readOnly: Boolean) -> Boolean,
  ): Boolean = true

  override fun delete(fileSpec: String, roamingType: RoamingType): Boolean = true
}