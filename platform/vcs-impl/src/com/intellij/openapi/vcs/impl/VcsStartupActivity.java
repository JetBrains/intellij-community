// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.impl;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public interface VcsStartupActivity {
  ExtensionPointName<VcsStartupActivity> EP_NAME = ExtensionPointName.create("com.intellij.vcsStartupActivity");

  void runActivity(@NotNull Project project);

  /**
   * @see VcsInitObject#getOrder()
   */
  int getOrder();
}