package com.intellij.structuralsearch.plugin;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public final class StructuralSearchPlugin {
  private boolean searchInProgress;
  private boolean replaceInProgress;
  private boolean myDialogVisible;

  public boolean isSearchInProgress() {
    return searchInProgress;
  }

  public void setSearchInProgress(boolean searchInProgress) {
    this.searchInProgress = searchInProgress;
  }

  public boolean isReplaceInProgress() {
    return replaceInProgress;
  }

  public void setReplaceInProgress(boolean replaceInProgress) {
    this.replaceInProgress = replaceInProgress;
  }

  public boolean isDialogVisible() {
    return myDialogVisible;
  }

  public void setDialogVisible(boolean dialogVisible) {
    myDialogVisible = dialogVisible;
  }

  public static StructuralSearchPlugin getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, StructuralSearchPlugin.class);
  }
}
