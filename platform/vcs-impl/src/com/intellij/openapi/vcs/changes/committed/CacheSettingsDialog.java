// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.committed;

import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public class CacheSettingsDialog {
  public static boolean showSettingsDialog(@NotNull Project project) {
    CacheSettingsPanel configurable = new CacheSettingsPanel();
    configurable.initPanel(project);

    return ShowSettingsUtil.getInstance().editConfigurable(project, configurable);
  }
}
