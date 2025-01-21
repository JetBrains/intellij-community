// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform;

import com.intellij.ide.util.projectWizard.SettingsStep;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.ui.ValidationInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class GeneratorPeerImpl<T> implements ProjectGeneratorPeer<T> {
  private static final Object DEFAULT_SETTINGS = new Object();
  private final @NotNull T mySettings;
  private final @NotNull JComponent myComponent;

  public GeneratorPeerImpl(final @NotNull T settings, final @NotNull JComponent component) {
    mySettings = settings;
    myComponent = component;
  }

  public GeneratorPeerImpl() {
    //noinspection unchecked
    this((T)DEFAULT_SETTINGS, new JPanel());
  }

  @Override
  public @NotNull JComponent getComponent(@NotNull TextFieldWithBrowseButton myLocationField, @NotNull Runnable checkValid) {
    return myComponent;
  }

  /**
   * @deprecated  It is here only for backward compatibility: some plugins still call it, call {@link #getComponent(TextFieldWithBrowseButton, Runnable)}
   */
  @Override
  @Deprecated
  public @NotNull JComponent getComponent() {
    return myComponent;
  }

  @Override
  public void buildUI(@NotNull SettingsStep settingsStep) {}

  @Override
  public @NotNull T getSettings() {
    return mySettings;
  }

  @Override
  public @Nullable ValidationInfo validate() {
    return null;
  }

  @Override
  public boolean isBackgroundJobRunning() {
    return false;
  }

  @Override
  public void addSettingsStateListener(@NotNull WebProjectGenerator.SettingsStateListener listener) {}
}
