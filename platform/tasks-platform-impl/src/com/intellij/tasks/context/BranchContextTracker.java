package com.intellij.tasks.context;

import com.intellij.notification.*;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.TransactionGuard;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.vcs.BranchChangeListener;
import com.intellij.openapi.vcs.VcsConfiguration;
import org.jetbrains.annotations.NotNull;

public class BranchContextTracker implements BranchChangeListener {

  public static final NotificationGroup NOTIFICATION = new NotificationGroup(
    "Branch Context group", NotificationDisplayType.BALLOON, true);

  private final WorkingContextManager myContextManager;
  private final Project myProject;
  private String myLastBranch;

  private BranchContextTracker(Project project) {
    myProject = project;
    myContextManager = WorkingContextManager.getInstance(project);
    project.getMessageBus().connect().subscribe(BranchChangeListener.VCS_BRANCH_CHANGED, this);
  }

  @Override
  public void branchWillChange(@NotNull String branchName) {
    myLastBranch = branchName;
    myContextManager.saveContext(getContextName(branchName), null);
  }

  @Override
  public void branchHasChanged(@NotNull String branchName) {
    VcsConfiguration vcsConfiguration = VcsConfiguration.getInstance(myProject);
    if (!vcsConfiguration.RELOAD_CONTEXT) return;

    String contextName = getContextName(branchName);
    if (!myContextManager.hasContext(contextName)) return;

    TransactionGuard.submitTransaction(myProject, () -> switchContext(branchName, contextName));
  }

  private void switchContext(@NotNull String branchName, String contextName) {
    myContextManager.clearContext();
    myContextManager.loadContext(contextName);

    Notification notification =
      NOTIFICATION.createNotification("Workspace is restored to how it was in the ‘" + branchName + "' branch", NotificationType.INFORMATION);
    if (myLastBranch != null && myContextManager.hasContext(getContextName(myLastBranch))) {
      notification.addAction(new NotificationAction("Rollback") {
        @Override
        public void actionPerformed(@NotNull AnActionEvent e, @NotNull Notification notification) {
          myContextManager.clearContext();
          myContextManager.loadContext(getContextName(myLastBranch));
        }
      });
    }
    notification.addAction(new NotificationAction("Configure...") {
      @Override
      public void actionPerformed(@NotNull AnActionEvent e, @NotNull Notification notification) {
        new ConfigureBranchContextDialog(myProject).show();
      }
    }).setContextHelpAction(new AnAction("What is a workspace?", "Workspace includes open editors, current run configuration, and breakpoints.", null) {
      @Override
      public void actionPerformed(AnActionEvent e) {

      }
    }).notify(myProject);
  }

  @NotNull
  private static String getContextName(String branchName) {
    return "__branch_context_" + branchName;
  }

  public static class TrackerStartupActivity implements StartupActivity{

    @Override
    public void runActivity(@NotNull Project project) {
      new BranchContextTracker(project);
    }
  }
}
