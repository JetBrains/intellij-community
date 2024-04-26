package com.intellij.remoteDev.downloader

import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

/**
 * Describes an installation of JetBrains Client. 
 * If [clientDir] is equal to [the home path][com.intellij.openapi.application.PathManager.getHomePath] of the current IDE instance, 
 * it means that an installation of JetBrains Client embedded in that instance should be used.  
 * 
 * TODO: extract a separate class to represent an embedded client installation.
 */
@ApiStatus.Internal
data class ExtractedJetBrainsClientData(
  val clientDir: Path,

  /**
   * jreDir is not required for newer clients, it's bundled in
   */
  val jreDir: Path?,
  val version: String,
)
