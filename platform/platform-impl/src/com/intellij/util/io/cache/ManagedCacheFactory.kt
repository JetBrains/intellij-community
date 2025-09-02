// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io.cache

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.util.io.DataExternalizer
import com.intellij.util.io.KeyDescriptor
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus.Internal
import java.nio.file.Path


@Internal
interface ManagedCacheFactory {

  fun <K, V> createCache(
    project: Project,
    basePath: Path,
    cacheName: String,
    keySerializer: KeyDescriptor<K>,
    valueSerializer: DataExternalizer<V>,
    serDeVersion: Int,
    coroutineScope: CoroutineScope,
  ): ManagedCache<K, V>

  companion object {
    private val EP_NAME = ExtensionPointName.create<ManagedCacheFactory>("com.intellij.managedCacheFactory")

    fun getDefault(): ManagedCacheFactory = ManagedPersistentCacheFactory

    fun getInstance(): ManagedCacheFactory {
      for (factory in EP_NAME.extensionList) {
        return factory
      }
      return getDefault()
    }
  }
}
