// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform;

import com.intellij.openapi.project.Project;
import com.intellij.projectImport.ProjectOpenProcessor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;

/**
 * Handles requests to open a non-project file from the command line.
 * This interface needs to be implemented by extensions registered in {@link ProjectOpenProcessor#EXTENSION_POINT_NAME} extension point.
 */
public interface CommandLineProjectOpenProcessor {
  /**
   * Opens a non-project file in a new project window.
   *
   * @param file the file to open
   * @param line the line to navigate to, -1 if undefined
   * @param column the column to navigate to, -1 if undefined
   * @param tempProject if {@code true}, always opens the file in a new temporary project, otherwise searches the parent directories
   *                    for `.idea` subdirectory, and if found, opens that directory.
   */
  @Nullable Project openProjectAndFile(@NotNull Path file, int line, int column, boolean tempProject);

  static CommandLineProjectOpenProcessor getInstance() {
    CommandLineProjectOpenProcessor extension = getInstanceIfExists();
    return extension == null ? PlatformProjectOpenProcessor.getInstance() : extension;
  }

  @Nullable static CommandLineProjectOpenProcessor getInstanceIfExists() {
    for (ProjectOpenProcessor extension : ProjectOpenProcessor.EXTENSION_POINT_NAME.getExtensionList()) {
      if (extension instanceof CommandLineProjectOpenProcessor) {
        return (CommandLineProjectOpenProcessor)extension;
      }
    }
    return null;
  }
}
