// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.platform.util.plugins

import com.intellij.util.lang.ZipFilePool
import org.jetbrains.annotations.ApiStatus
import java.io.IOException
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path

@ApiStatus.Internal
interface DataLoader {
  val pool: ZipFilePool?

  fun isExcludedFromSubSearch(jarFile: Path): Boolean = false

  @Throws(IOException::class)
  fun load(path: String): ByteArray?

  override fun toString(): String
}

@ApiStatus.Internal
class LocalFsDataLoader(val basePath: Path) : DataLoader {
  override val pool: ZipFilePool?
    get() = ZipFilePool.POOL

  override fun load(path: String): ByteArray? {
    return try {
      Files.readAllBytes(basePath.resolve(path))
    }
    catch (e: NoSuchFileException) {
      null
    }
  }

  override fun toString() = basePath.toString()
}