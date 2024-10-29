// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.BinFiles.devFilesDir
import com.intellij.util.BinFiles.getBinFile
import com.intellij.util.io.outputStream
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.NonNls
import java.nio.file.Path
import kotlin.io.path.exists


/**
 * Name of bin file i.e "server".
 * Use slashes `/` to separate file path. Path is always relative
 */
@JvmInline
@Internal
value class FileName(val value: @NonNls String) {
  override fun toString(): String = value
  internal val relativePath: @NonNls String get() = value.trimStart('/').trimEnd('/')
}

/**
 * Tool to use bundled binary files.
 * Publish your binary file packed in jar in maven (consider using Space), add dependency to it and use [getBinFile] to retrieve it.
 * For local development create file in [devFilesDir] in your home dir.
 */
@Internal
object BinFiles {
  private const val BIN_FILES_DIR_NAME = "IJBinFiles"
  private val lock = Any()
  private val tempDir: Lazy<Path> = lazy<Path> {
    FileUtil.createTempDirectory(BIN_FILES_DIR_NAME, null, true).toPath()
  }


  /**
   * Dir with file for local development
   */
  val devFilesDir: Path get() = PathManager.getSystemDir().resolve(BIN_FILES_DIR_NAME)

  private val logger = thisLogger()

  /**
   * Load [fileName] either from [BIN_FILES_DIR_NAME] or classpath of [clazz].
   * Make sure file is accessible from [clazz] class loader
   */
  fun getBinFile(fileName: FileName, clazz: Class<*>): Path = synchronized(lock) {
    val devFile = devFilesDir.resolve(fileName.relativePath)
    if (devFile.exists()) {
      logger.info("Using dev $devFile")
      return@synchronized devFile
    }

    val localCopyOfFile = tempDir.value.resolve(fileName.relativePath)
    if (localCopyOfFile.exists()) {
      return@synchronized localCopyOfFile
    }

    val resource = clazz.classLoader.getResource(fileName.relativePath) ?: throw IllegalArgumentException("$fileName is not in the class path")
    resource.openStream().use { input ->
      localCopyOfFile.outputStream().use { output ->
        input.copyTo(output)
      }
    }
    return@synchronized localCopyOfFile
  }
}