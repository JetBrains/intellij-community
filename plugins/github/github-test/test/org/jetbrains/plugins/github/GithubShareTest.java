package org.jetbrains.plugins.github;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.testFramework.TestActionEvent;
import git4idea.test.TestDialogHandler;
import git4idea.test.TestNotificator;
import org.jetbrains.plugins.github.test.GithubTest;
import org.jetbrains.plugins.github.ui.GithubShareDialog;

import static git4idea.test.GitTestUtil.assertNotification;

/**
 * @author Kirill Likhodedov
 */
public class GithubShareTest extends GithubTest {

  private static final String PROJECT_NAME = "new_project_from_test";

  @Override
  public void setUp() throws Exception {
    super.setUp();
  }

  @Override
  public void tearDown() throws Exception {
    removeCreatedProject();
    super.tearDown();
  }

  private void removeCreatedProject() {
    // TODO: send a request to GitHub to remove the created project
  }

  public void testSimpleCase() throws Throwable {
    myDialogManager.registerDialogHandler(GithubShareDialog.class, new TestDialogHandler<GithubShareDialog>() {
      @Override
      public int handleDialog(GithubShareDialog dialog) {
        dialog.setRepositoryName(PROJECT_NAME);
        return DialogWrapper.OK_EXIT_CODE;
      }
    });

    // TODO: refactor the production code to avoid programmatically invoking the action with a fake data context
    AnAction shareAction = ActionManager.getInstance().getAction("Github.Share");
    shareAction.actionPerformed(new TestActionEvent(shareAction));

    assertNotification(myProject, new Notification(TestNotificator.TEST_NOTIFICATION_GROUP, "Success",
                                                   "Successfully created project '" + PROJECT_NAME + "' on GitHub",
                                                   NotificationType.INFORMATION));
    // TODO check that the project was created on github, and the repository was created locally and pushed there
  }

}
