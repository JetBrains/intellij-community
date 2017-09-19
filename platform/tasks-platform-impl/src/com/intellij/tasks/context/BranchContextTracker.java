package com.intellij.tasks.context;

import com.intellij.notification.NotificationDisplayType;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.vcs.BranchChangeListener;
import com.intellij.openapi.vcs.VcsConfiguration;
import org.jetbrains.annotations.NotNull;

import javax.swing.event.HyperlinkEvent;

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

    myContextManager.clearContext();
    myContextManager.loadContext(contextName);
    String content = "User interface layout has been restored to what it was in branch ‘" + branchName + "'<br>";
    if (myLastBranch != null && myContextManager.hasContext(getContextName(myLastBranch))) {
      content += "<a href='restore'>Rollback to '" + myLastBranch + "' layout</a>&nbsp;&nbsp;";
    }
    content += "<a href='configure'>Configure</a>";

    NOTIFICATION.createNotification(null, null, content, NotificationType.INFORMATION, (notification, event) -> {
      if (event.getEventType() != HyperlinkEvent.EventType.ACTIVATED) return;
      if ("configure".equals(event.getDescription())) {
        new ConfigureBranchContextDialog(myProject).show();
      }
      else if ("restore".equals(event.getDescription())) {
        myContextManager.clearContext();
        myContextManager.loadContext(getContextName(myLastBranch));
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
