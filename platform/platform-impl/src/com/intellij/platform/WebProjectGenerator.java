/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
import com.intellij.ide.util.projectWizard.SettingsStep;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * Extend this class to contribute web project generator to IDEA (available via File -> 'Add Module...' -> 'Web Module')
 * and to small IDE (PhpStorm, WebStorm etc. available via File -> 'New Project...').
 *
 * @author Sergey Simonchik
 */
public abstract class WebProjectGenerator<T> implements DirectoryProjectGenerator<T> {

  @NotNull
  @Nls
  @Override
  public abstract String getName();

  public abstract String getDescription();

  @Nullable
  public Integer getPreferredDescriptionWidth() {
    return null;
  }

  @Nullable
  public String getHelpId() {
    return null;
  }

  @Override
  public abstract void generateProject(@NotNull Project project, @NotNull VirtualFile baseDir, @NotNull T settings, @NotNull Module module);

  /**
   * Always returns {@link ValidationResult#OK}.
   * Real validation should be done in {@link WebProjectGenerator.GeneratorPeer#validate()}.
   */
  @NotNull
  @Override
  public final ValidationResult validate(@NotNull String baseDirPath) {
    return ValidationResult.OK;
  }

  @NotNull
  public abstract GeneratorPeer<T> createPeer();

  public boolean isPrimaryGenerator() {
    return true;
  }

  public interface GeneratorPeer<T> {
    @NotNull
    JComponent getComponent();

    void buildUI(@NotNull SettingsStep settingsStep);

    @NotNull
    T getSettings();

    // null if ok
    @Nullable
    ValidationInfo validate();

    boolean isBackgroundJobRunning();

    void addSettingsStateListener(@NotNull SettingsStateListener listener);
  }

  public interface SettingsStateListener {
    void stateChanged(boolean validSettings);
  }
}
