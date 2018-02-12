package org.jetbrains.plugins.github;

import com.intellij.notification.NotificationType;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.TestDialog;
import git4idea.commands.Git;
import org.jetbrains.plugins.github.api.GithubApiUtil;
import org.jetbrains.plugins.github.api.GithubConnection;
import org.jetbrains.plugins.github.api.data.GithubRepoDetailed;
import org.jetbrains.plugins.github.util.GithubAuthData;

import java.io.IOException;

import static com.intellij.openapi.vcs.Executor.cd;
import static git4idea.test.GitExecutor.git;

/**
 * @author Aleksey Pivovarov
 */
public class GithubShareProjectTest extends GithubShareProjectTestBase {

  public void testSimple() throws Throwable {
    registerDefaultShareDialogHandler();
    registerDefaultUntrackedFilesDialogHandler();

    createProjectFiles();

    GithubShareAction.shareProjectOnGithub(myProject, projectRoot);

    checkNotification(NotificationType.INFORMATION, "Successfully shared project on GitHub", null);
    initGitChecks();
    checkGitExists();
    checkGithubExists();
    checkRemoteConfigured();
    checkLastCommitPushed();
  }

  public void testGithubAlreadyExists() {
    final boolean[] dialogShown = new boolean[1];
    TestDialog oldTestDialog = Messages.setTestDialog(new TestDialog() {
      @Override
      public int show(String message) {
        dialogShown[0] = message.contains("Successfully connected to") && message.contains("Do you want to proceed anyway?");
        return 1;
      }
    });

    try {
      registerDefaultShareDialogHandler();
      registerDefaultUntrackedFilesDialogHandler();

      createProjectFiles();
      GithubShareAction.shareProjectOnGithub(myProject, projectRoot);
      assertFalse(dialogShown[0]);

      GithubShareAction.shareProjectOnGithub(myProject, projectRoot);
      assertTrue(dialogShown[0]);
    }
    finally {
      Messages.setTestDialog(oldTestDialog);
    }
  }

  public void testExistingGit() throws Throwable {
    registerDefaultShareDialogHandler();
    registerDefaultUntrackedFilesDialogHandler();

    createProjectFiles();

    cd(projectRoot.getPath());
    git("init");
    setGitIdentity(projectRoot);
    git("add file.txt");
    git("commit -m init");

    GithubShareAction.shareProjectOnGithub(myProject, projectRoot);

    checkNotification(NotificationType.INFORMATION, "Successfully shared project on GitHub", null);
    initGitChecks();
    checkGitExists();
    checkGithubExists();
    checkRemoteConfigured();
    checkLastCommitPushed();
  }

  public void testExistingFreshGit() throws Throwable {
    registerDefaultShareDialogHandler();
    registerDefaultUntrackedFilesDialogHandler();

    createProjectFiles();

    Git.getInstance().init(myProject, projectRoot);

    GithubShareAction.shareProjectOnGithub(myProject, projectRoot);

    checkNotification(NotificationType.INFORMATION, "Successfully shared project on GitHub", null);
    initGitChecks();
    checkGitExists();
    checkGithubExists();
    checkRemoteConfigured();
    checkLastCommitPushed();
  }

  public void testEmptyProject() throws Throwable {
    registerSelectNoneUntrackedFilesDialogHandler();
    registerDefaultShareDialogHandler();

    GithubShareAction.shareProjectOnGithub(myProject, projectRoot);

    checkNotification(NotificationType.INFORMATION, "Successfully created empty repository on GitHub", null);
    initGitChecks();
    checkGitExists();
    checkGithubExists();
    checkRemoteConfigured();
  }

  protected void checkGithubExists() throws IOException {
    GithubAuthData auth = myGitHubSettings.getAuthData();
    GithubRepoDetailed githubInfo = GithubApiUtil.getDetailedRepoInfo(new GithubConnection(auth), myLogin1, PROJECT_NAME);
    assertNotNull("GitHub repository does not exist", githubInfo);
  }
}
