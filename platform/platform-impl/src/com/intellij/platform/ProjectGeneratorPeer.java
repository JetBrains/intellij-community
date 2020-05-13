// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

  /**
   * @return {@code null} if OK
   */
  @Nullable
  ValidationInfo validate();

  boolean isBackgroundJobRunning();

  interface SettingsListener {
    void stateChanged(boolean validSettings);
  }

  /**
   * Adds settings state listener to validate user input.
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
   * @deprecated use {@link #addSettingsListener(SettingsListener)} method instead
   */
  @SuppressWarnings("DeprecatedIsStillUsed")
  @Deprecated
  default void addSettingsStateListener(@NotNull WebProjectGenerator.SettingsStateListener listener) {}
}
