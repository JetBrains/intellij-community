// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.update;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.openapi.project.DumbAwareRunnable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectReloadState;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.changes.committed.CommittedChangesCache;
import com.intellij.openapi.vcs.ex.ProjectLevelVcsManagerEx;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@State(name = "RestoreUpdateTree", storages = {
  @Storage(StoragePathMacros.CACHE_FILE),
  @Storage(value = StoragePathMacros.WORKSPACE_FILE, deprecated = true),
})
public class RestoreUpdateTree implements PersistentStateComponent<Element> {
  private UpdateInfo myUpdateInfo;

  public RestoreUpdateTree(@NotNull Project project, @NotNull StartupManager startupManager) {
    startupManager.registerPostStartupActivity((DumbAwareRunnable)() -> {
      if (myUpdateInfo != null && !myUpdateInfo.isEmpty() && ProjectReloadState.getInstance(project).isAfterAutomaticReload()) {
        ActionInfo actionInfo = myUpdateInfo.getActionInfo();
        if (actionInfo != null) {
          ProjectLevelVcsManagerEx.getInstanceEx(project).showUpdateProjectInfo(myUpdateInfo.getFileInformation(),
                                                                                  VcsBundle.message("action.display.name.update"), actionInfo,
                                                                                  false);
          CommittedChangesCache.getInstance(project).refreshIncomingChangesAsync();
        }
      }
      myUpdateInfo = null;
    });
  }

  public static RestoreUpdateTree getInstance(Project project) {
    return project.getComponent(RestoreUpdateTree.class);
  }

  @Nullable
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
