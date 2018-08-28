// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.platform;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.projectImport.ProjectOpenProcessor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;

/**
 * Handles requests to open a project from the command line. This interface needs to be implemented by extensions registered
 * in {@link ProjectOpenProcessor#EXTENSION_POINT_NAME} extension point.
 */
public interface CommandLineProjectOpenProcessor {
  @Nullable
  Project doOpenProject(@NotNull VirtualFile file,
                        @Nullable Project projectToClose,
                        int line,
                        @NotNull EnumSet<PlatformProjectOpenProcessor.Option> options);

  static CommandLineProjectOpenProcessor getInstance() {
    for (ProjectOpenProcessor extension : ProjectOpenProcessor.EXTENSION_POINT_NAME.getExtensions()) {
      if (extension instanceof CommandLineProjectOpenProcessor) {
        return (CommandLineProjectOpenProcessor)extension;
      }
    }
    return PlatformProjectOpenProcessor.getInstance();
  }
}
