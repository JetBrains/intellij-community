// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package com.intellij.idea

import com.google.common.util.concurrent.Striped
import com.intellij.util.lang.ImmutableZipFile
import com.intellij.util.lang.ZipFile
import com.intellij.util.lang.ZipFilePool
import org.jetbrains.annotations.ApiStatus
import java.io.InputStream
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

@ApiStatus.Internal
class ZipFilePoolImpl : ZipFilePool() {
  private val pool = ConcurrentHashMap<Path, MyEntryResolver>(1024)
  private val lock = Striped.lock(64)

  override fun loadZipFile(file: Path): ZipFile {
    val resolver = pool.get(file)
    // doesn't make sense to use pool for requests from class loader (requested only once per class loader)
    return resolver?.zipFile ?: ImmutableZipFile.load(file)
  }

  override fun load(file: Path): EntryResolver {
    pool.get(file)?.let { return it }
    val lock = lock.get(file)
    lock.lock()
    try {
      return pool.computeIfAbsent(file) {
        val zipFile = ImmutableZipFile.load(file)
        MyEntryResolver(zipFile)
      }
    }
    finally {
      lock.unlock()
    }
  }

  private class MyEntryResolver(@JvmField val zipFile: ZipFile) : EntryResolver {
    override fun loadZipEntry(path: String): InputStream? {
      return zipFile.getInputStream(if (path[0] == '/') path.substring(1) else path)
    }

    override fun toString() = zipFile.toString()
  }

  fun clear() {
    pool.clear()
  }
}