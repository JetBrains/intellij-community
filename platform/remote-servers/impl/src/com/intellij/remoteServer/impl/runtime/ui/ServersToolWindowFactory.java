/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.remoteServer.impl.runtime.ui;

import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import org.jetbrains.annotations.NotNull;

public class ServersToolWindowFactory implements ToolWindowFactory, Condition<Project>, DumbAware {

  private final RemoteServersViewContribution myContribution;

  public ServersToolWindowFactory(RemoteServersViewContribution contribution) {
    myContribution = contribution;
  }

  @Override
  public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
    final ContentFactory contentFactory = ContentFactory.SERVICE.getInstance();
    final ServersToolWindowContent serversContent = doCreateToolWindowContent(project);
    Content content = contentFactory.createContent(serversContent.getMainPanel(), null, false);
    content.setHelpId(getContribution().getContextHelpId());
    Disposer.register(content, serversContent);
    toolWindow.getContentManager().addContent(content);
  }

  @NotNull
  protected ServersToolWindowContent doCreateToolWindowContent(@NotNull Project project) {
    return new ServersToolWindowContent(project, myContribution, ServersToolWindowContent.ActionGroups.SHARED_ACTION_GROUPS);
  }

  @Override
  public boolean value(Project project) {
    return myContribution.canContribute(project);
  }

  public RemoteServersViewContribution getContribution() {
    return myContribution;
  }
}
