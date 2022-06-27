// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.project;

import com.intellij.openapi.startup.StartupActivity;
import org.jetbrains.annotations.NotNull;

final class DumbServiceStartupActivity implements StartupActivity {
  @Override
  public void runActivity(@NotNull Project project) {
    DumbServiceImpl dumbService = (DumbServiceImpl)DumbService.getInstance(project);
    dumbService.queueStartupActivitiesRequiredForSmartMode();
  }
}
