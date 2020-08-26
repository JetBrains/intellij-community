// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.vcs.versionBrowser;

import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public interface ChangesBrowserSettingsEditor<T extends ChangeBrowserSettings> {
  @NotNull
  JComponent getComponent();

  @NotNull
  T getSettings();

  void setSettings(@NotNull T settings);

  @Nls
  @Nullable
  String validateInput();

  void updateEnabledControls();

  @NotNull
  String getDimensionServiceKey();
}