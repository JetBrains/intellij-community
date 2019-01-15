// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.win;

import com.intellij.ide.impl.ProjectUtil;
import com.intellij.openapi.application.ApplicationStarterBase;
import org.jetbrains.annotations.Nullable;

/**
 * @author Denis Fokin
 */
public class RecentProjectApplication extends ApplicationStarterBase {
  public RecentProjectApplication() {
    super("reopen", 1);
  }

  @Override
  public String getUsageMessage() {
    return "This command is used for internal purpose only.";
  }

  @Override
  protected void processCommand(String[] args, @Nullable String currentDirectory) {
    ProjectUtil.openProject(args[1], null, false);
  }
}