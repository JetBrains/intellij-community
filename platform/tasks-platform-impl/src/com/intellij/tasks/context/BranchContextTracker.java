package com.intellij.tasks.context;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.vcs.BranchChangeListener;
import org.jetbrains.annotations.NotNull;

public class BranchContextTracker implements BranchChangeListener, StartupActivity {

  private WorkingContextManager myContextManager;

  @Override
  public void runActivity(@NotNull Project project) {
    myContextManager = WorkingContextManager.getInstance(project);
    project.getMessageBus().connect().subscribe(BranchChangeListener.VCS_BRANCH_CHANGED, this);
  }

  @Override
  public void branchWillChange(@NotNull String branchName) {
    myContextManager.saveContext(getContextName(branchName), null);
  }

  @Override
  public void branchHasChanged(@NotNull String branchName) {
    myContextManager.loadContext(getContextName(branchName));
  }

  @NotNull
  private static String getContextName(String branchName) {
    return "__branch_context_" + branchName;
  }
}
