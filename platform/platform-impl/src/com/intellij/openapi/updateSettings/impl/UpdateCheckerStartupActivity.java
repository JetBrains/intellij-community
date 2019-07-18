// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.updateSettings.impl;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class UpdateCheckerStartupActivity implements StartupActivity {
  @Override
  public void runActivity(@NotNull Project project) {
    UpdateCheckerService.getInstance().checkForUpdates();

  }
}
