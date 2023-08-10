// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform

import com.intellij.ide.impl.OpenProjectTask
import com.intellij.openapi.project.Project
import com.intellij.projectImport.ProjectOpenProcessor
import java.nio.file.Path

/**
 * Handles requests to open a non-project file from the command line.
 * This interface needs to be implemented by extensions registered in [ProjectOpenProcessor.EXTENSION_POINT_NAME] extension point.
 */
interface CommandLineProjectOpenProcessor {
  /**
   * Opens a non-project file in a new project window.
   *
   * @param file the file to open
   * @param tempProject if `true`, always opens the file in a new temporary project, otherwise searches the parent directories
   * for `.idea` subdirectory, and if found, opens that directory.
   */
  suspend fun openProjectAndFile(file: Path, tempProject: Boolean, options: OpenProjectTask = OpenProjectTask()): Project?

  companion object {
    fun getInstance(): CommandLineProjectOpenProcessor = getInstanceIfExists() ?: PlatformProjectOpenProcessor.getInstance()

    fun getInstanceIfExists(): CommandLineProjectOpenProcessor? {
      return ProjectOpenProcessor.EXTENSION_POINT_NAME.getIterable()
        .asSequence()
        .filterIsInstance<CommandLineProjectOpenProcessor>()
        .firstOrNull()
    }
  }
}