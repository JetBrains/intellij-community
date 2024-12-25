// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform;

import com.intellij.ide.util.projectWizard.SettingsStep;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.ui.ValidationInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public interface ProjectGeneratorPeer<T> {
  /**
   * Returns a new project settings component.
   * If a component is a dialog panel from Kotlin DSL UI, its validation state will be used
   */
  default @NotNull JComponent getComponent(final @NotNull TextFieldWithBrowseButton myLocationField, final @NotNull Runnable checkValid) {
    return getComponent();
  }

  /**
   * @deprecated implement {@link #getComponent(TextFieldWithBrowseButton, Runnable)} instead
   */
  @Deprecated
  default @NotNull JComponent getComponent() {
    throw new RuntimeException("Do not use this method, use the one above instead");
  }

  void buildUI(@NotNull SettingsStep settingsStep);

  /**
   * Returns new project settings
   */
  @NotNull
  T getSettings();

  /**
   * if {@link #getComponent(TextFieldWithBrowseButton, Runnable)} is Kotlin DSL UI panel, then it will also be validated,
   * and this method must check only things not covered by the panel.
   *
   * @return {@code null} if OK.
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
  default void addSettingsStateListener(@NotNull WebProjectGenerator.SettingsStateListener listener) { }
}
