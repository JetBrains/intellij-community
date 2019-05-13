// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.platform;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.projectImport.ProjectOpenProcessor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Handles requests to open a non-project file from the command line. This interface needs to be implemented by extensions registered
 * in {@link ProjectOpenProcessor#EXTENSION_POINT_NAME} extension point.
 */
public interface CommandLineProjectOpenProcessor {
  /**
   * Opens a non-project file in a new project window.
   *
   * @param file the file to open
   * @param line the line to navigate to
   * @param tempProject if true, always opens the file in a new temporary project. If false, searches the parent directories of the file
   *                    for a .idea subdirectory, and if found, opens that directory.
   */
  @Nullable
  Project openProjectAndFile(@NotNull VirtualFile file, int line, boolean tempProject);

  static CommandLineProjectOpenProcessor getInstance() {
    CommandLineProjectOpenProcessor extension = getInstanceIfExists();
    return extension != null ? extension : PlatformProjectOpenProcessor.getInstance();
  }

  @Nullable
  static CommandLineProjectOpenProcessor getInstanceIfExists() {
    for (ProjectOpenProcessor extension : ProjectOpenProcessor.EXTENSION_POINT_NAME.getExtensions()) {
      if (extension instanceof CommandLineProjectOpenProcessor) {
        return (CommandLineProjectOpenProcessor)extension;
      }
    }
    return null;
  }
}
