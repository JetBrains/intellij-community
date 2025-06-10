// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.zombie

import com.intellij.openapi.diagnostic.runAndLogException
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

internal class CompositeZombieCache<Z: Zombie>(
  private val caches: List<Cache<Z>>,
) : Cache<Z> {
  override suspend fun put(key: Int, value: FingerprintedZombie<Z>) = caches.forEachAsync { it.put(key, value) }

  override suspend fun get(key: Int): FingerprintedZombie<Z>? = caches.firstNotNullOfOrNull { it.get(key) }

  override suspend fun remove(key: Int): Unit = caches.forEachAsync { it.remove(key) }

  private suspend fun List<Cache<Z>>.forEachAsync(operation: suspend (Cache<Z>) -> Unit) = coroutineScope {
    forEach {
      async {
        LOG.runAndLogException {
          operation(it)
        }
      }
    }
  }

  companion object {
    private val LOG = thisLogger()

    @Suppress("UNCHECKED_CAST")
    fun <Z: Zombie> create(cacheName: String, necromancy: Necromancy<Z>, project: Project, coroutineScope: CoroutineScope)  = CompositeZombieCache<Z>(
      ZombieCacheFactory.EP.extensionList.mapNotNull {
        LOG.runAndLogException {
          (it as ZombieCacheFactory<Z>).createCache(cacheName, necromancy, project, coroutineScope)
        }
      }
    )
  }
}