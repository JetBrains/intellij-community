package com.intellij.remoteDev.downloader

import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

@ApiStatus.Internal
data class ExtractedJetBrainsClientData(
  val clientDir: Path,

  /**
   * jreDir is not required for newer clients, it's bundled in
   */
  val jreDir: Path?,
  val version: String,
)
