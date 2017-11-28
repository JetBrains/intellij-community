/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
