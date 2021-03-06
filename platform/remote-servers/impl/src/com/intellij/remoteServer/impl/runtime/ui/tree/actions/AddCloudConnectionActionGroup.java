// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.remoteServer.impl.runtime.ui.tree.actions;

import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.remoteServer.CloudBundle;
import com.intellij.remoteServer.ServerType;
import com.intellij.remoteServer.impl.runtime.ui.DefaultRemoteServersServiceViewContributor;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.intellij.remoteServer.impl.runtime.ui.RemoteServersServiceViewContributor.addNewRemoteServer;

public class AddCloudConnectionActionGroup extends ActionGroup {
  @Override
  public AnAction @NotNull [] getChildren(@Nullable AnActionEvent e) {
    List<ServerType> serverTypes = ContainerUtil.filter(ServerType.EP_NAME.getExtensionList(),
                                                        type -> type.getCustomToolWindowId() == null &&
                                                                type.createDefaultConfiguration().getCustomToolWindowId() == null);
    AnAction[] actions = new AnAction[serverTypes.size()];
    for (int i = 0; i < serverTypes.size(); i++) {
      actions[i] = new AddCloudConnectionAction(serverTypes.get(i));
    }
    return actions;
  }

  private static class AddCloudConnectionAction extends DumbAwareAction {
    private final ServerType<?> myServerType;

    AddCloudConnectionAction(ServerType<?> serverType) {
      super(serverType.getPresentableName(), CloudBundle.message("AddCloudConnectionAction.description", serverType.getPresentableName()),
            serverType.getIcon());
      myServerType = serverType;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      if (e.getPlace().equals(ActionPlaces.ACTION_SEARCH)) {
        e.getPresentation().setText(CloudBundle.messagePointer("new.cloud.connection.configurable.title", myServerType.getPresentableName()));
      }
      else {
        e.getPresentation().setText(myServerType.getPresentableName());
      }
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      Project project = e.getProject();
      if (project == null) return;

      addNewRemoteServer(project, myServerType, DefaultRemoteServersServiceViewContributor.class);
    }
  }
}
