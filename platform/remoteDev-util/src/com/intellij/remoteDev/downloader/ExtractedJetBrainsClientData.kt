// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.remoteDev.downloader

import java.nio.file.Path

/**
 * Describes an installation of JetBrains Client. 
 * If [clientDir] is equal to [the home path][com.intellij.openapi.application.PathManager.getHomePath] of the current IDE instance, 
 * it means that an installation of JetBrains Client embedded in that instance should be used.  
 * 
 * TODO: extract a separate class to represent an embedded client installation.
 */
@Deprecated("Use FrontendInstallation instead")
data class ExtractedJetBrainsClientData(
  val clientDir: Path,

  /**
   * jreDir is not required for newer clients, it's bundled in
   */
  val jreDir: Path?,
  val version: String,
)
