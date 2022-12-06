// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.impl;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import org.jetbrains.annotations.NotNull;

/**
 * Ordered pipeline for initialization of VCS-related services.
 * Typically, should not be needed by plugins.
 *
 * @see ProjectLevelVcsManager#runAfterInitialization(Runnable)
 * @see StartupActivity.Background
 */
public interface VcsStartupActivity {
  void runActivity(@NotNull Project project);

  /**
   * @see VcsInitObject#getOrder()
   */
  int getOrder();
}