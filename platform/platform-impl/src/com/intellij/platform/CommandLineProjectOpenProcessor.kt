// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform

import com.intellij.ide.impl.OpenProjectTask
import com.intellij.openapi.project.Project
import com.intellij.projectImport.ProjectOpenProcessor
import java.nio.file.Path
import org.jetbrains.annotations.ApiStatus

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
   *
   * @return project or null if it cannot be opened with that open processor
   */
  suspend fun openProjectAndFile(file: Path, tempProject: Boolean, options: OpenProjectTask = OpenProjectTask()): Project?

  /**
   * Returns the project that takes [file], a regular file that comes from outside the IDE.
   * Returns `null` when this processor has no preference.
   *
   * The platform asks every processor before it selects a project on its own, also when no project is open.
   * An implementation can open a project for the file. The caller opens the file in the returned project.
   */
  @ApiStatus.Internal
  suspend fun findProjectForExternalFile(file: Path): Project? = null

  @ApiStatus.Internal
  companion object {
    /** Returns the first answer of [CommandLineProjectOpenProcessor.findProjectForExternalFile] among the registered processors. */
    suspend fun findProjectForExternalFile(file: Path): Project? {
      return ProjectOpenProcessor.EXTENSION_POINT_NAME.getIterable()
        .asSequence()
        .filterIsInstance<CommandLineProjectOpenProcessor>()
        .firstNotNullOfOrNull {
          it.findProjectForExternalFile(file)
        }
    }

    suspend fun openProjectAndFile(file: Path, tempProject: Boolean, options: OpenProjectTask = OpenProjectTask()): Project? {
      val project = ProjectOpenProcessor.EXTENSION_POINT_NAME.getIterable()
        .asSequence()
        .filterIsInstance<CommandLineProjectOpenProcessor>()
        .firstNotNullOfOrNull {
          it.openProjectAndFile(file, tempProject, options)
        }
      return project
    }
  }
}