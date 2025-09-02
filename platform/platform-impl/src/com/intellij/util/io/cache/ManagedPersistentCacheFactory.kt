// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io.cache

import com.intellij.openapi.project.Project
import com.intellij.openapi.project.getProjectCacheFileName
import com.intellij.util.io.DataExternalizer
import com.intellij.util.io.KeyDescriptor
import com.intellij.util.io.PersistentMapBuilder
import kotlinx.coroutines.CoroutineScope
import java.nio.file.Path


internal object ManagedPersistentCacheFactory : ManagedCacheFactory {

  override fun <K, V> createCache(
    project: Project,
    basePath: Path,
    cacheName: String,
    keySerializer: KeyDescriptor<K>,
    valueSerializer: DataExternalizer<V>,
    serDeVersion: Int,
    coroutineScope: CoroutineScope,
  ): ManagedCache<K, V> {
    val (cacheUniqName, cachePath) = cacheNameAndPath(project, basePath, cacheName)
    val builder = PersistentMapBuilder.newBuilder(
      cachePath,
      keySerializer,
      valueSerializer,
    ).withVersion(serDeVersion)
    return ManagedPersistentCache(cacheUniqName, builder, coroutineScope)
  }

  private fun cacheNameAndPath(project: Project, basePath: Path, cacheName: String): Pair<String, Path> {
    // IJPL-157893 the cache should survive project renaming
    val projectName = project.getProjectCacheFileName(hashSeparator="-")
    val projectPath = basePath.resolve(projectName)
    val cacheUniqName = "$cacheName-$projectName" // name should be unique across the application
    val cachePath = projectPath.resolve(cacheName).resolve(cacheName) // TODO: why name is twice here?
    return cacheUniqName to cachePath
  }
}
