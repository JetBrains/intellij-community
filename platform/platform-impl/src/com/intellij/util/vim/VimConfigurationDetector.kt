// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.vim

import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.io.FileUtilRt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Files
import java.nio.file.Paths

@ApiStatus.Internal
object VimConfigurationDetector {
  /**
   * Return a path to the vimrc configuration in format `~/.vimrc`.
   * The path is not intended to be used to resolve it, but show in the UI if needed.
   *
   * If no configuration is found, NULL is returned
   */
  suspend fun detectVimrcConfiguration(): @NlsSafe String? {
    val homeDir = Paths.get(System.getProperty("user.home"))
    return withContext(Dispatchers.IO) {
      vimConfigFiles.firstOrNull { Files.exists(homeDir.resolve(it)) }?.let { FileUtilRt.toSystemDependentName("~/$it") }
    }
  }

  private val vimConfigFiles = listOf(
    ".vimrc",
    "_vimrc",
    ".vim/vimrc",
  )
}