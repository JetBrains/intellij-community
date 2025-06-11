// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.zombie

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope

interface RemoteCacheFactory<Z: Zombie> {
  fun createCache(cacheName: String, necromancy: Necromancy<Z>, project: Project, coroutineScope: CoroutineScope): RemoteCache<Z>
  companion object {
    val EP: ExtensionPointName<RemoteCacheFactory<*>> = ExtensionPointName<RemoteCacheFactory<*>>("com.intellij.remoteManagedCacheFactory")
  }
}