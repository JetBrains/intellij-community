/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.lang.ant.config.impl;

import com.intellij.lang.ant.config.explorer.AntExplorer;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class AntToolWindowFactory implements ToolWindowFactory, DumbAware{
  @Override
  public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
    AntExplorer explorer = new AntExplorer(project);
    final ContentManager contentManager = toolWindow.getContentManager();
    final Content content = contentManager.getFactory().createContent(explorer, null, false);
    contentManager.addContent(content);
    toolWindow.setHelpId(HelpID.ANT);
    Disposer.register(project, explorer);
  }
}
