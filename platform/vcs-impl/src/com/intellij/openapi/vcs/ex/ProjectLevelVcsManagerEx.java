// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.ex;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsConfiguration;
import com.intellij.openapi.vcs.impl.projectlevelman.PersistentVcsShowConfirmationOption;
import com.intellij.openapi.vcs.impl.projectlevelman.PersistentVcsShowSettingOption;
import com.intellij.openapi.vcs.update.ActionInfo;
import com.intellij.openapi.vcs.update.UpdateInfoTree;
import com.intellij.openapi.vcs.update.UpdatedFiles;
import com.intellij.ui.content.ContentManager;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public abstract class ProjectLevelVcsManagerEx extends ProjectLevelVcsManager {
  @Topic.ProjectLevel
  public static final Topic<VcsActivationListener> VCS_ACTIVATED =
    new Topic<>(VcsActivationListener.class, Topic.BroadcastDirection.NONE);

  public static ProjectLevelVcsManagerEx getInstanceEx(Project project) {
    return (ProjectLevelVcsManagerEx)project.getService(ProjectLevelVcsManager.class);
  }

  @Nullable
  public abstract ContentManager getContentManager();

  @NotNull
  public abstract PersistentVcsShowSettingOption getOptions(VcsConfiguration.StandardOption option);

  @NotNull
  public abstract PersistentVcsShowConfirmationOption getConfirmation(VcsConfiguration.StandardConfirmation option);

  @NotNull
  public abstract List<PersistentVcsShowSettingOption> getAllOptions();

  @NotNull
  public abstract List<PersistentVcsShowConfirmationOption> getAllConfirmations();

  public abstract void notifyDirectoryMappingChanged();

  @RequiresEdt
  @Nullable
  public abstract UpdateInfoTree showUpdateProjectInfo(UpdatedFiles updatedFiles,
                                                       @Nls String displayActionName,
                                                       ActionInfo actionInfo,
                                                       boolean canceled);

  public abstract void scheduleMappedRootsUpdate();

  public abstract void fireDirectoryMappingsChanged();

  public abstract String haveDefaultMapping();

  /**
   * Whether vcs mappings were already processed after opening the project.
   * ie: if true, one can assume that {@link #hasActiveVcss()} and {@link #hasAnyMappings()} match if the mappings are correct.
   * <p>
   * See {@link #VCS_ACTIVATED} listener that will be notified when this value changes.
   */
  public abstract boolean areVcsesActivated();
}
