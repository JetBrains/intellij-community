// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.zombie

import com.intellij.ide.caches.CachesInvalidator
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.io.NioFiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.concurrent.atomic.AtomicBoolean

private val LOG: Logger = logger<NecropolisDestroyer>()
private fun markerFile() = Necropolis.necropolisPath().resolve(".invalidated")

@Service(Service.Level.APP)
internal class NecropolisDestroyer {
  private val cleaned = AtomicBoolean(false)
  private val mutex = Mutex()

  suspend fun cleanGravesIfNeeded() {
    if (cleaned.get()) {
      return
    }
    mutex.withLock {
      if (cleaned.get()) {
        return
      }
      val markerFile = markerFile()
      withContext(Dispatchers.IO) {
        if (Files.exists(markerFile)) {
          cleanGraves(markerFile.parent)
        }
      }
      cleaned.set(true)
    }
  }

  private fun cleanGraves(cachePath: Path) {
    LOG.info("destroying necropolis")
    try {
      NioFiles.deleteRecursively(cachePath)
    } catch (e: IOException) {
      LOG.error("destroying necropolis error ${cachePath.fileName}", e)
    }
  }

  override fun toString() = "NecropolisDestroyer(cleaned=${cleaned.get()})"

  internal class InvalidationRequest : CachesInvalidator() {
    override fun invalidateCaches() {
      markInvalidated()
    }

    private fun markInvalidated() {
      val markerFile = markerFile()
      try {
        Files.createDirectories(markerFile.parent)
        Files.write(markerFile, ByteArray(0), StandardOpenOption.WRITE, StandardOpenOption.CREATE)
      } catch (e: IOException) {
        LOG.error("error while creating invalidation marker file", e)
      }
    }
  }
}
