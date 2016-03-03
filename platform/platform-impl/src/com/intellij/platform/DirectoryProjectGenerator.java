/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.platform;

import com.intellij.facet.ui.ValidationResult;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author yole
 */
public interface DirectoryProjectGenerator<T> {
  ExtensionPointName<DirectoryProjectGenerator> EP_NAME = ExtensionPointName.create("com.intellij.directoryProjectGenerator");

  @NotNull
  @Nls
  String getName();

  /**
   * @deprecated todo[vokin]: delete in 2016.3
   */
  @Nullable
  default T showGenerationSettings(final VirtualFile baseDir) throws ProcessCanceledException {
    return null;
  }

  /**
   * @return 16x16 icon or null, if no icon is available
   */
  @Nullable
  Icon getLogo();

  void generateProject(@NotNull final Project project, @NotNull final VirtualFile baseDir,
                       @Nullable final T settings, @NotNull final Module module);

  @NotNull
  ValidationResult validate(@NotNull String baseDirPath);
}
