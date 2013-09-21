/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.update;

import com.intellij.lifecycle.PeriodicalTasksCloser;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.project.DumbAwareRunnable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectReloadState;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.RoamingTypeDisabled;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.changes.committed.CommittedChangesCache;
import com.intellij.openapi.vcs.ex.ProjectLevelVcsManagerEx;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class RestoreUpdateTree implements ProjectComponent, JDOMExternalizable, RoamingTypeDisabled {
  private final Project myProject;

  private UpdateInfo myUpdateInfo;
  @NonNls private static final String UPDATE_INFO = "UpdateInfo";

  public RestoreUpdateTree(Project project) {
    myProject = project;
  }

  @Override
  public void projectOpened() {
    StartupManager.getInstance(myProject).registerPostStartupActivity(new DumbAwareRunnable() {
      @Override
      public void run() {
        if (myUpdateInfo != null && !myUpdateInfo.isEmpty() && ProjectReloadState.getInstance(myProject).isAfterAutomaticReload()) {
          ActionInfo actionInfo = myUpdateInfo.getActionInfo();
          if (actionInfo != null) {
            ProjectLevelVcsManagerEx.getInstanceEx(myProject).showUpdateProjectInfo(myUpdateInfo.getFileInformation(),
                                                                                    VcsBundle.message("action.display.name.update"), actionInfo,
                                                                                    false);
            CommittedChangesCache.getInstance(myProject).refreshIncomingChangesAsync();
          }
          myUpdateInfo = null;
        }
        else {
          myUpdateInfo = null;
        }
      }
    });
  }

  @Override
  public void projectClosed() {

  }

  @Override
  @NotNull
  public String getComponentName() {
    return "RestoreUpdateTree";
  }

  @Override
  public void initComponent() { }

  @Override
  public void disposeComponent() {
  }

  @Override
  public void readExternal(Element element) throws InvalidDataException {
    Element child = element.getChild(UPDATE_INFO);
    if (child != null) {
        UpdateInfo updateInfo = new UpdateInfo(myProject);
        updateInfo.readExternal(child);
        myUpdateInfo = updateInfo;
      }
  }

  @Override
  public void writeExternal(Element element) throws WriteExternalException {
    if (myUpdateInfo != null) {
      Element child = new Element(UPDATE_INFO);
      element.addContent(child);
      myUpdateInfo.writeExternal(child);
    }
  }


  public static RestoreUpdateTree getInstance(Project project) {
    return PeriodicalTasksCloser.getInstance().safeGetComponent(project, RestoreUpdateTree.class);
  }

  public void registerUpdateInformation(UpdatedFiles updatedFiles, ActionInfo actionInfo) {
    myUpdateInfo = new UpdateInfo(myProject, updatedFiles, actionInfo);
  }
}
