// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl.text

import com.dynatrace.hash4j.hashing.Hashing
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.io.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

@ApiStatus.Internal
abstract class TextEditorCache<T>(private val project: Project, private val scope: CoroutineScope) {
  protected val cache: ManagedCache<Int, T> = createCache()

  abstract fun namePrefix(): String
  abstract fun valueExternalizer(): ValueExternalizer<T>
  abstract fun useHeapCache(): Boolean

  interface ValueExternalizer<T> : DataExternalizer<T> {
    fun serdeVersion(): Int
  }

  companion object {
    fun Document.contentHash(): Int = Hashing.komihash5_0().hashCharsToInt(this.immutableCharSequence)
    fun cachePath(): Path = PathManager.getSystemDir().resolve("editor")
  }

  private fun createCache(): ManagedCache<Int, T> {
    val graveName = namePrefix()
    val projectName = project.uniqueProjectName()
    val cacheName = "$graveName-$projectName"
    val cachePath = cachePath().resolve(graveName).resolve(projectName)
    val builder = PersistentMapBuilder.newBuilder(
      cachePath,
      EnumeratorIntegerDescriptor.INSTANCE,
      valueExternalizer()
    ).withVersion(valueExternalizer().serdeVersion())
    val cache = if (useHeapCache()) {
      ManagedHeapPersistentCache(cacheName, builder)
    } else {
      ManagedPersistentCache(cacheName, builder)
    }
    scope.launch(Dispatchers.IO) {
      cache.forceOnTimer(periodMs=5_000)
    }
    return cache
  }

  private fun Project.uniqueProjectName(): String {
    return this.name.trimLongString() + "-" + this.locationHash.trimLongString()
  }

  private fun String.trimLongString(): String = StringUtil.shortenTextWithEllipsis(this, 50, 10, "")
    .replace(Regex("[^\\p{IsAlphabetic}\\d]"), "")
    .replace(" ", "")
    .replace(StringUtil.NON_BREAK_SPACE, "")
}
