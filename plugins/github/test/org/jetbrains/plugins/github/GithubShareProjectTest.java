package org.jetbrains.plugins.github;

import com.intellij.notification.NotificationType;
import com.intellij.openapi.components.ServiceManager;
import git4idea.commands.Git;
import org.jetbrains.plugins.github.api.GithubApiUtil;
import org.jetbrains.plugins.github.api.GithubRepoDetailed;
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

    GithubShareAction.shareProjectOnGithub(myProject, myProjectRoot);

    checkNotification(NotificationType.INFORMATION, "Successfully shared project on GitHub", null);
    initGitChecks();
    checkGitExists();
    checkGithubExists();
    checkRemoteConfigured();
    checkLastCommitPushed();
  }

  public void testGithubAlreadyExists() throws Throwable {
    registerDefaultShareDialogHandler();
    registerDefaultUntrackedFilesDialogHandler();

    createProjectFiles();
    GithubShareAction.shareProjectOnGithub(myProject, myProjectRoot);
    GithubShareAction.shareProjectOnGithub(myProject, myProjectRoot);

    checkNotification(NotificationType.INFORMATION, "Project is already on GitHub", null);
  }

  public void testExistingGit() throws Throwable {
    registerDefaultShareDialogHandler();
    registerDefaultUntrackedFilesDialogHandler();

    createProjectFiles();

    cd(myProjectRoot.getPath());
    git("init");
    setGitIdentity(myProjectRoot);
    git("add file.txt");
    git("commit -m init");

    GithubShareAction.shareProjectOnGithub(myProject, myProjectRoot);

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

    Git git = ServiceManager.getService(Git.class);
    git.init(myProject, myProjectRoot);

    GithubShareAction.shareProjectOnGithub(myProject, myProjectRoot);

    checkNotification(NotificationType.INFORMATION, "Successfully shared project on GitHub", null);
    initGitChecks();
    checkGitExists();
    checkGithubExists();
    checkRemoteConfigured();
    checkLastCommitPushed();
  }

  public void testEmptyProject() throws Throwable {
    registerDefaultUntrackedFilesDialogHandler();
    registerDefaultShareDialogHandler();

    GithubShareAction.shareProjectOnGithub(myProject, myProjectRoot);

    checkNotification(NotificationType.INFORMATION, "Successfully created empty repository on GitHub", null);
    initGitChecks();
    checkGitExists();
    checkGithubExists();
    checkRemoteConfigured();
  }

  protected void checkGithubExists() throws IOException {
    GithubAuthData auth = myGitHubSettings.getAuthData();
    GithubRepoDetailed githubInfo = GithubApiUtil.getDetailedRepoInfo(auth, myLogin1, PROJECT_NAME);
    assertNotNull("GitHub repository does not exist", githubInfo);
  }
}
