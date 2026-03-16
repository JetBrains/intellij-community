// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.zombie

import com.intellij.openapi.project.Project
import com.intellij.util.io.EnumeratorIntegerDescriptor
import com.intellij.util.io.cache.ManagedCache
import com.intellij.util.io.cache.ManagedCacheFactory
import kotlinx.coroutines.CoroutineScope


private typealias Cache<Z> = ManagedCache<Int, FingerprintedZombie<Z>>

class GraveImpl<Z : Zombie>(
  private val graveName: String,
  private val necromancy: Necromancy<Z>,
  private val project: Project,
  private val coroutineScope: CoroutineScope,
) : Grave<Z> {

  private val cache: Cache<Z> = createCache()

  override suspend fun buryZombie(id: Int, zombie: FingerprintedZombie<Z>?) {
    if (zombie == null) {
      cache.remove(id)
    } else {
      cache.put(id, zombie)
    }
  }

  override suspend fun exhumeZombie(id: Int): FingerprintedZombie<Z>? {
    return cache.get(id)
  }

  private fun createCache(): Cache<Z> {
    val cacheFactory = ManagedCacheFactory.getInstance()
    return cacheFactory.createCache(
      project,
      Necropolis.necropolisPath(),
      graveName,
      EnumeratorIntegerDescriptor.INSTANCE,
      FingerprintedZombieImpl.Externalizer(necromancy),
      necromancy.spellLevel(),
      coroutineScope,
    )
  }
}
