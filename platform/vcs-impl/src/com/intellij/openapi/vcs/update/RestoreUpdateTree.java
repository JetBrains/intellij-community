// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.update;

import com.intellij.openapi.components.*;
import com.intellij.openapi.project.DumbAwareRunnable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectReloadState;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.changes.committed.CommittedChangesCache;
import com.intellij.openapi.vcs.ex.ProjectLevelVcsManagerEx;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@State(
  name = "RestoreUpdateTree",
  storages = @Storage(StoragePathMacros.WORKSPACE_FILE)
)
public class RestoreUpdateTree implements ProjectComponent, PersistentStateComponent<Element> {
  private final Project myProject;

  private UpdateInfo myUpdateInfo;

  public RestoreUpdateTree(Project project) {
    myProject = project;
  }

  public static RestoreUpdateTree getInstance(Project project) {
    return project.getComponent(RestoreUpdateTree.class);
  }

  @Override
  public void projectOpened() {
    StartupManager.getInstance(myProject).registerPostStartupActivity((DumbAwareRunnable)() -> {
      if (myUpdateInfo != null && !myUpdateInfo.isEmpty() && ProjectReloadState.getInstance(myProject).isAfterAutomaticReload()) {
        ActionInfo actionInfo = myUpdateInfo.getActionInfo();
        if (actionInfo != null) {
          ProjectLevelVcsManagerEx.getInstanceEx(myProject).showUpdateProjectInfo(myUpdateInfo.getFileInformation(),
                                                                                  VcsBundle.message("action.display.name.update"), actionInfo,
                                                                                  false);
          CommittedChangesCache.getInstance(myProject).refreshIncomingChangesAsync();
        }
      }
      myUpdateInfo = null;
    });
  }

  @Override
  @NotNull
  public String getComponentName() {
    return "RestoreUpdateTree";
  }

  @Nullable
  @Override
  public Element getState() {
    Element element = new Element("state");
    if (myUpdateInfo != null && !myUpdateInfo.isEmpty()) {
      try {
        myUpdateInfo.writeExternal(element);
      }
      catch (WriteExternalException e) {
        throw new RuntimeException(e);
      }
    }
    return element;
  }

  @Override
  public void loadState(@NotNull Element state) {
    UpdateInfo updateInfo = new UpdateInfo();
    updateInfo.readExternal(state);
    myUpdateInfo = updateInfo.isEmpty() ? null : updateInfo;
  }

  public void registerUpdateInformation(UpdatedFiles updatedFiles, ActionInfo actionInfo) {
    myUpdateInfo = new UpdateInfo(updatedFiles, actionInfo);
  }
}
