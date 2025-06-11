// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.zombie

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.util.io.DataExternalizer
import com.intellij.util.io.cache.RemoteManagedCache
import kotlinx.coroutines.CoroutineScope

interface RemoteCacheFactory {
  fun<K, V> createCache(
    cacheName: String,
    keyExternalizer: DataExternalizer<K>,
    valueExternalizer: DataExternalizer<V>,
    project: Project,
    coroutineScope: CoroutineScope
  ): RemoteManagedCache<K, V>
  companion object {
    val EP: ExtensionPointName<RemoteCacheFactory> = ExtensionPointName<RemoteCacheFactory>("com.intellij.remoteManagedCacheFactory")
    fun<K, V> tryCreateCache(
      cacheName: String,
      keyExternalizer: DataExternalizer<K>,
      valueExternalizer: DataExternalizer<V>,
      project: Project,
      coroutineScope: CoroutineScope
    ): RemoteManagedCache<K, V>? {
      val remoteCacheFactory = EP.findFirstSafe { true }
      return remoteCacheFactory?.createCache(cacheName, keyExternalizer, valueExternalizer, project, coroutineScope)
    }
  }
}