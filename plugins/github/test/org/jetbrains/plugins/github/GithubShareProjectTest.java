package org.jetbrains.plugins.github;

import com.intellij.notification.NotificationType;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Ref;
import git4idea.commands.Git;
import git4idea.test.TestDialogHandler;
import org.jetbrains.plugins.github.api.GithubApiUtil;
import org.jetbrains.plugins.github.api.data.GithubRepoDetailed;

import java.io.IOException;

import static com.intellij.openapi.vcs.Executor.cd;

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
    Ref<Boolean> shown = Ref.create(false);
    dialogManager.registerDialogHandler(GithubShareAction.GithubExistingRemotesDialog.class,
                                        new TestDialogHandler<GithubShareAction.GithubExistingRemotesDialog>() {
                                          @Override
                                          public int handleDialog(GithubShareAction.GithubExistingRemotesDialog dialog) {
                                            shown.set(true);
                                            return DialogWrapper.OK_EXIT_CODE;
                                          }
                                        });

    registerDefaultShareDialogHandler();
    registerDefaultUntrackedFilesDialogHandler();

    createProjectFiles();
    GithubShareAction.shareProjectOnGithub(myProject, projectRoot);
    assertFalse(shown.get());

    GithubShareAction.shareProjectOnGithub(myProject, projectRoot);
    assertTrue(shown.get());
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
    GithubRepoDetailed githubInfo = myApiTaskExecutor.execute(myAccount, c -> {
      String username = GithubApiUtil.getCurrentUser(c).getLogin();
      return GithubApiUtil.getDetailedRepoInfo(c, username, PROJECT_NAME);
    });
    assertNotNull("GitHub repository does not exist", githubInfo);
  }
}
