// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.impl;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.ProjectActivity;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import org.jetbrains.annotations.NotNull;

/**
 * An ordered pipeline for initialization of VCS-related services.
 * Typically, should not be needed by plugins.
 *
 * @see ProjectLevelVcsManager#runAfterInitialization(Runnable)
 * @see ProjectActivity
 */
public interface VcsStartupActivity {
  void runActivity(@NotNull Project project);

  /**
   * @see VcsInitObject#getOrder()
   */
  int getOrder();
}