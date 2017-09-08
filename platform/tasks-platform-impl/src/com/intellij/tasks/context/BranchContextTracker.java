package com.intellij.tasks.context;

import com.intellij.notification.NotificationDisplayType;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.vcs.BranchChangeListener;
import org.jetbrains.annotations.NotNull;

import javax.swing.event.HyperlinkEvent;

public class BranchContextTracker implements BranchChangeListener {

  public static final NotificationGroup NOTIFICATION = new NotificationGroup(
    "Branch Context group", NotificationDisplayType.STICKY_BALLOON, true);

  private final WorkingContextManager myContextManager;
  private final Project myProject;

  private BranchContextTracker(Project project) {
    myProject = project;
    myContextManager = WorkingContextManager.getInstance(project);
    project.getMessageBus().connect().subscribe(BranchChangeListener.VCS_BRANCH_CHANGED, this);
  }

  @Override
  public void branchWillChange(@NotNull String branchName) {
    myContextManager.saveContext(getContextName(branchName), null);
  }

  @Override
  public void branchHasChanged(@NotNull String branchName) {
    String contextName = getContextName(branchName);
    if (!myContextManager.hasContext(contextName)) return;

    myContextManager.clearContext();
    myContextManager.loadContext(contextName);
    NOTIFICATION.createNotification(null, null, "Branch context has been loaded<br>" +
                                                "<a href='ok'>Got it!</a><br>" +
                                                "<a href='off'>Turn off the feature</a>", NotificationType.INFORMATION, (notification, event) -> {
      if (event.getEventType() != HyperlinkEvent.EventType.ACTIVATED) return;
      if ("ok".equals(event.getDescription())) {
        notification.expire();
      }
      if ("off".equals(event.getDescription())) {
        notification.expire();
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
