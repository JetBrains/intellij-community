package com.intellij.openapi.vcs.ex;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.update.ActionInfo;
import com.intellij.openapi.vcs.update.UpdateInfoTree;
import com.intellij.openapi.vcs.update.UpdatedFiles;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.ui.content.ContentManager;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public abstract class ProjectLevelVcsManagerEx extends ProjectLevelVcsManager {
  public static ProjectLevelVcsManagerEx getInstanceEx(Project project) {
    return (ProjectLevelVcsManagerEx)project.getComponent(ProjectLevelVcsManager.class);
  }

  public static ProjectLevelVcsManagerEx getInstanceChecked(final Project project) {
    return ApplicationManager.getApplication().runReadAction(new Computable<ProjectLevelVcsManagerEx>() {
      public ProjectLevelVcsManagerEx compute() {
        if (project.isDisposed()) throw new ProcessCanceledException();
        return getInstanceEx(project);
      }
    });
  }

  public abstract ContentManager getContentManager();

  @NotNull
  public abstract VcsShowSettingOption getOptions(VcsConfiguration.StandardOption option);

  @NotNull
  public abstract VcsShowConfirmationOptionImpl getConfirmation(VcsConfiguration.StandardConfirmation option);

  public abstract List<VcsShowOptionsSettingImpl> getAllOptions();

  public abstract List<VcsShowConfirmationOptionImpl> getAllConfirmations();

  public abstract void notifyDirectoryMappingChanged();

  public abstract UpdateInfoTree showUpdateProjectInfo(UpdatedFiles updatedFiles, String displayActionName, ActionInfo actionInfo);

  public abstract void fireDirectoryMappingsChanged();

  public abstract String haveDefaultMapping();
}