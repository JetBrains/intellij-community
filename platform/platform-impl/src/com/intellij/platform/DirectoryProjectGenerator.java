// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.platform;

import com.intellij.facet.ui.ValidationResult;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author yole
 */
public interface DirectoryProjectGenerator<T> {
  ExtensionPointName<DirectoryProjectGenerator> EP_NAME = ExtensionPointName.create("com.intellij.directoryProjectGenerator");

  @Nullable
  default String getDescription() {
    return null;
  }

  @Nullable
  default String getHelpId() {
    return null;
  }

  // to be removed in 2017.3
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2017.3")
  default boolean isPrimaryGenerator() {
    return true;
  }

  @NotNull
  String getName();

  @NotNull
  default NotNullLazyValue<ProjectGeneratorPeer<T>> createLazyPeer() {
    return new NotNullLazyValue<ProjectGeneratorPeer<T>>() {
      @NotNull
      @Override
      protected ProjectGeneratorPeer<T> compute() {
        return createPeer();
      }
    };
  }

  /**
   * Creates new peer - new project settings and UI for them
   */
  @NotNull
  default ProjectGeneratorPeer<T> createPeer() {
    return new GeneratorPeerImpl<>();
  }

  /**
   * @return 16x16 icon or null, if no icon is available
   */
  @Nullable
  Icon getLogo();

  void generateProject(@NotNull final Project project,
                       @NotNull final VirtualFile baseDir,
                       @NotNull final T settings,
                       @NotNull final Module module);

  @NotNull
  ValidationResult validate(@NotNull String baseDirPath);
}
