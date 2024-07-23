// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.zombie

import com.dynatrace.hash4j.hashing.Hashing
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.getProjectCacheFileName
import com.intellij.util.io.DataExternalizer
import com.intellij.util.io.DataInputOutputUtil.readLONG
import com.intellij.util.io.DataInputOutputUtil.writeLONG
import com.intellij.util.io.EnumeratorIntegerDescriptor
import com.intellij.util.io.PersistentMapBuilder
import com.intellij.util.io.cache.ManagedCache
import com.intellij.util.io.cache.ManagedPersistentCache
import kotlinx.coroutines.CoroutineScope
import java.io.DataInput
import java.io.DataOutput
import java.nio.file.Path

typealias Cache<Z> = ManagedCache<Int, FingerprintedZombie<Z>>

class GraveImpl<Z : Zombie> (
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
    val (cacheName, cachePath) = cacheNameAndPath()
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

  private fun cacheNameAndPath(): Pair<String, Path> {
    // IJPL-157893 the cache should survive project renaming
    val projectName = project.getProjectCacheFileName(hashSeparator="-")
    val projectPath = necropolisPath().resolve(projectName)
    val cacheName = "$graveName-$projectName" // name should be unique across the application
    val cachePath = projectPath.resolve(graveName).resolve(graveName)
    return cacheName to cachePath
  }
}

data class FingerprintedZombieImpl<Z : Zombie>(
  private val fingerprint: Long,
  private val zombie: Z,
) : FingerprintedZombie<Z> {

  companion object {
    fun captureFingerprint(documentContent: CharSequence): Long {
      return Hashing.komihash5_0().hashCharsToLong(documentContent)
    }
  }

  override fun fingerprint(): Long = fingerprint
  override fun zombie(): Z = zombie
}

internal class FingerprintedExternalizer<Z : Zombie>(
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
