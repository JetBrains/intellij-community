// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.platform;

import com.intellij.facet.ui.ValidationResult;
import com.intellij.ide.util.projectWizard.SettingsStep;
import com.intellij.openapi.ui.ValidationInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * Extend this class to contribute web project generator to IDEA (available via File -> 'Add Module...' -> 'Web Module')
 * and to small IDE (PhpStorm, WebStorm etc. available via File -> 'New Project...').
 *
 * @author Sergey Simonchik
 *
 * Deprecated since 2017.3. Please use 'WebProjectTemplate' class instead
 */
@Deprecated
public abstract class WebProjectGenerator<T> extends DirectoryProjectGeneratorBase<T> {

  /**
   * Always returns {@link ValidationResult#OK}.
   * Real validation should be done in {@link WebProjectGenerator.GeneratorPeer#validate()}.
   */
  @NotNull
  @Override
  public final ValidationResult validate(@NotNull String baseDirPath) {
    return ValidationResult.OK;
  }

  @Override
  public boolean isPrimaryGenerator() {
    return true;
  }

  @Override
  public abstract String getDescription();

  /**
   * Deprecated since 2017.3. Please use 'ProjectGeneratorPeer' class instead
   */
  @Deprecated
  public interface GeneratorPeer<T> extends ProjectGeneratorPeer<T> {
    @Override
    @NotNull
    JComponent getComponent();

    @Override
    void buildUI(@NotNull SettingsStep settingsStep);

    @Override
    @NotNull
    T getSettings();

    // null if ok
    @Override
    @Nullable
    ValidationInfo validate();

    @Override
    boolean isBackgroundJobRunning();

    @Override
    void addSettingsStateListener(@NotNull SettingsStateListener listener);
  }

  /*
   * Deprecated since 2017.3. Please use 'ProjectGeneratorPeer.SettingsListener' class instead
   */
  @Deprecated
  public interface SettingsStateListener {
    void stateChanged(boolean validSettings);
  }
}
