// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.update;

import com.intellij.openapi.components.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectReloadState;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.changes.committed.CommittedChangesCache;
import com.intellij.openapi.vcs.ex.ProjectLevelVcsManagerEx;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

@State(name = "RestoreUpdateTree", storages = @Storage(StoragePathMacros.CACHE_FILE))
@Service
public final class RestoreUpdateTree implements PersistentStateComponent<Element> {
  private UpdateInfo myUpdateInfo;

  static final class MyStartUpActivity implements StartupActivity.DumbAware {
    @Override
    public void runActivity(@NotNull Project project) {
      RestoreUpdateTree instance = getInstance(project);
      UpdateInfo updateInfo = instance.myUpdateInfo;
      if (updateInfo != null && !updateInfo.isEmpty() && ProjectReloadState.getInstance(project).isAfterAutomaticReload()) {
        ActionInfo actionInfo = updateInfo.getActionInfo();
        if (actionInfo != null) {
          ProjectLevelVcsManagerEx projectLevelVcsManager = ProjectLevelVcsManagerEx.getInstanceEx(project);
          projectLevelVcsManager.showUpdateProjectInfo(updateInfo.getFileInformation(), VcsBundle.message("action.display.name.update"), actionInfo, false);
          CommittedChangesCache.getInstance(project).refreshIncomingChangesAsync();
        }
      }
      instance.myUpdateInfo = null;
    }
  }

  public static RestoreUpdateTree getInstance(@NotNull Project project) {
    return project.getService(RestoreUpdateTree.class);
  }

  @NotNull
  @Override
  public Element getState() {
    Element element = new Element("state");
    if (myUpdateInfo != null && !myUpdateInfo.isEmpty()) {
      myUpdateInfo.writeExternal(element);
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
