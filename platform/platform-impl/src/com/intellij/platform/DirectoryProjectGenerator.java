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
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author yole
 */
public abstract class DirectoryProjectGenerator<T> {
  public static final ExtensionPointName<DirectoryProjectGenerator> EP_NAME = ExtensionPointName.create("com.intellij.directoryProjectGenerator");

  @Deprecated
  @Nullable
  public Integer getPreferredDescriptionWidth() {
    return null;
  }

  /**
   * @deprecated todo[vokin]: delete in 2016.3
   */
  @Nullable
  T showGenerationSettings(final VirtualFile baseDir) throws ProcessCanceledException {
    return null;
  }

  @Nullable
  public String getDescription() {
    return null;
  }

  @Nullable
  public String getHelpId() {
    return null;
  }

  @Deprecated
  public boolean isPrimaryGenerator() {
    return true;
  }

  @NotNull
  @Nls
  public abstract String getName();

  @NotNull
  public final NotNullLazyValue<ProjectGeneratorPeer<T>> createLazyPeer() {
    return new NotNullLazyValue<ProjectGeneratorPeer<T>>() {
      @NotNull
      @Override
      protected ProjectGeneratorPeer<T> compute() {
        return createPeer();
      }
    };
  }

  @NotNull
  protected ProjectGeneratorPeer<T> createPeer() {
    return new GeneratorPeerImpl<>();
  }

  /**
   * @return 16x16 icon or null, if no icon is available
   */
  @Nullable
  public abstract Icon getLogo();

  public abstract void generateProject(@NotNull final Project project,
                                       @NotNull final VirtualFile baseDir,
                                       @Nullable final T settings,
                                       @NotNull final Module module);

  @NotNull
  public ValidationResult validate(@NotNull String baseDirPath) {
    return ValidationResult.OK;
  }
}
