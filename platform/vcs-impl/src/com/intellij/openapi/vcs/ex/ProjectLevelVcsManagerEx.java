// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.ex;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsConfiguration;
import com.intellij.openapi.vcs.impl.projectlevelman.NewMappings;
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
  @SuppressWarnings("LoggerInitializedWithForeignClass")
  public static final Logger MAPPING_DETECTION_LOG = Logger.getInstance(NewMappings.class);

  @Topic.ProjectLevel
  public static final Topic<VcsActivationListener> VCS_ACTIVATED =
    new Topic<>(VcsActivationListener.class, Topic.BroadcastDirection.NONE);

  public static ProjectLevelVcsManagerEx getInstanceEx(Project project) {
    return (ProjectLevelVcsManagerEx)ProjectLevelVcsManager.getInstance(project);
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

  /**
   * @deprecated A plugin should not need to call this.
   */
  @Deprecated
  public abstract void notifyDirectoryMappingChanged();

  @RequiresEdt
  @Nullable
  public abstract UpdateInfoTree showUpdateProjectInfo(UpdatedFiles updatedFiles,
                                                       @Nls String displayActionName,
                                                       ActionInfo actionInfo,
                                                       boolean canceled);

  public abstract void scheduleMappedRootsUpdate();

  /**
   * @deprecated A plugin should not need to call this.
   */
  @Deprecated
  public abstract void fireDirectoryMappingsChanged();

  /**
   * @return {@link AbstractVcs#getName()} for &lt;Project&gt; mapping if configured;
   * empty string for &lt;None&gt; &lt;Project&gt; mapping;
   * null if no default mapping is configured.
   */
  @Nullable
  public abstract String haveDefaultMapping();
}
