package com.intellij.tasks.context;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.BranchChangeListener;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import org.jetbrains.annotations.NotNull;

public class BranchContextTracker implements BranchChangeListener {

  private final WorkingContextManager myContextManager;

  public static void install(Project project) {
    new BranchContextTracker(project);
  }

  private BranchContextTracker(Project project) {
    myContextManager = WorkingContextManager.getInstance(project);
    project.getMessageBus().connect().subscribe(ProjectLevelVcsManager.VCS_BRANCH_CHANGED, this);
  }

  @Override
  public void branchWillChange(String branchName) {
    myContextManager.saveContext(getContextName(branchName), null);
  }

  @Override
  public void branchDidChange(String branchName) {
    myContextManager.loadContext(getContextName(branchName));
  }

  @NotNull
  private static String getContextName(String branchName) {
    return "__branch_context_" + branchName;
  }
}
