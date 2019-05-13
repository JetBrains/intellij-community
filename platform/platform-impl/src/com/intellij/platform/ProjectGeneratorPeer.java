/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.ide.util.projectWizard.SettingsStep;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.ui.ValidationInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public interface ProjectGeneratorPeer<T> {
  @NotNull
  default JComponent getComponent(@NotNull final TextFieldWithBrowseButton myLocationField, @NotNull final Runnable checkValid) {
    return getComponent();
  }

  /**
   * Returns new project settings component
   */
  @NotNull
  JComponent getComponent();

  void buildUI(@NotNull SettingsStep settingsStep);

  /**
   * Returns new project settings
   */
  @NotNull
  T getSettings();

  // null if ok
  @Nullable
  ValidationInfo validate();

  boolean isBackgroundJobRunning();

  interface SettingsListener {
    void stateChanged(boolean validSettings);
  }

  /**
   * Adds settings state listener to validate user input
   */
  default void addSettingsListener(@NotNull SettingsListener listener) {
    addSettingsStateListener(new WebProjectGenerator.SettingsStateListener() {
      @Override
      public void stateChanged(boolean validSettings) {
        listener.stateChanged(validSettings);
      }
    });
  }

  /**
   * Please use {@link #addSettingsListener(SettingsListener)} method instead
   */
  @Deprecated
  void addSettingsStateListener(@NotNull WebProjectGenerator.SettingsStateListener listener);
}
