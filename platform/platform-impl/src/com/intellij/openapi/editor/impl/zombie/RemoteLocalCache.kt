// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.zombie

import com.intellij.openapi.project.Project
import com.intellij.util.io.DataExternalizer
import com.intellij.util.io.DataInputOutputUtil.readLONG
import com.intellij.util.io.DataInputOutputUtil.writeLONG
import com.intellij.util.io.EnumeratorIntegerDescriptor
import com.intellij.util.io.PersistentMapBuilder
import com.intellij.util.io.cache.ManagedPersistentCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.io.DataInput
import java.io.DataOutput

internal class RemoteLocalCache<Z: Zombie>(
  private val localCache: Cache<Z>,
  private val remoteCache: RemoteCache<Z>?
) : Cache<Z> {
  override suspend fun put(key: Int, value: FingerprintedZombie<Z>) {
    coroutineScope {
      async { localCache.put(key, value) }
      async { remoteCache?.put(key, value) }
    }
  }

  suspend fun prefetch(id: Int) {
    if (localCache.get(id) != null) return
    remoteCache?.get(id)?.let { localCache.put(id, it) }
  }

  override suspend fun get(key: Int): FingerprintedZombie<Z>? {
    return localCache.get(key) ?: remoteCache?.get(key)?.also { localCache.put(key, it) }
  }

  override suspend fun remove(key: Int) {
    coroutineScope {
      async { localCache.remove(key) }
      async { remoteCache?.remove(key) }
    }
  }

  companion object {
    @Suppress("UNCHECKED_CAST")
    fun <Z: Zombie> create(
      cacheName: String,
      necromancy: Necromancy<Z>,
      project: Project,
      coroutineScope: CoroutineScope
    ): RemoteLocalCache<Z> {
      val localCache =  createLocalCache(cacheName, necromancy, project, coroutineScope)
      val remoteCache = RemoteCacheFactory
        .tryCreateCache<Int, FingerprintedZombie<Z>>(
          cacheName,
          EnumeratorIntegerDescriptor.INSTANCE,
          FingerprintedExternalizer(necromancy),
          project,
          coroutineScope
        )
      remoteCache?.preload(coroutineScope, localCache)
      return RemoteLocalCache(
        localCache = localCache,
        remoteCache = remoteCache,
      )
    }
    private fun<Z: Zombie> RemoteCache<Z>.preload(coroutineScope: CoroutineScope, localCache: Cache<Z>) = coroutineScope.launch {
        val preloadedFromReload = preloadedContent.await()
        preloadedFromReload.forEach { (key, value) -> localCache.put(key, value) }
      }

    private fun<Z: Zombie> createLocalCache(cacheName: String, necromancy: Necromancy<Z>, project: Project, coroutineScope: CoroutineScope): Cache<Z> {
      val (cacheName, cachePath) = necropolisCacheNameAndPath(cacheName, project)
      val builder = PersistentMapBuilder.newBuilder(
        cachePath,
        EnumeratorIntegerDescriptor.INSTANCE,
        FingerprintedExternalizer(necromancy),
      ).withVersion(necromancy.spellLevel())
      return if (necromancy.isDeepBury()) {
        ManagedPersistentCache(cacheName, builder, coroutineScope)
      } else {
        // TODO: heap implementation
        ManagedPersistentCache(cacheName, builder, coroutineScope)
      }
    }
  }
}

private class FingerprintedExternalizer<Z : Zombie>(
  private val necromancy: Necromancy<Z>,
) : DataExternalizer<FingerprintedZombie<Z>> {

  override fun read(input: DataInput): FingerprintedZombie<Z> {
    val fingerprint = readLONG(input)
    val zombie = necromancy.exhumeZombie(input)
    return FingerprintedZombieImpl(fingerprint, zombie)
  }

  override fun save(output: DataOutput, value: FingerprintedZombie<Z>) {
    writeLONG(output, value.fingerprint())
    necromancy.buryZombie(output, value.zombie())
  }
}