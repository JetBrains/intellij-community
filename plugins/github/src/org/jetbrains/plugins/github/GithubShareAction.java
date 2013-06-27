/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.github;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.VcsShowConfirmationOption;
import com.intellij.openapi.vcs.changes.ui.SelectFilesDialog;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ThrowableConsumer;
import com.intellij.util.containers.HashSet;
import git4idea.GitUtil;
import git4idea.GitVcs;
import git4idea.Notificator;
import git4idea.actions.BasicAction;
import git4idea.actions.GitInit;
import git4idea.commands.*;
import git4idea.i18n.GitBundle;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import git4idea.util.GitFileUtils;
import git4idea.util.GitUIUtil;
import icons.GithubIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.github.ui.GithubShareDialog;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.jetbrains.plugins.github.GithubUtil.setVisibleEnabled;

/**
 * @author oleg
 */
public class GithubShareAction extends DumbAwareAction {
  private static final Logger LOG = Logger.getInstance(GithubShareAction.class.getName());

  public GithubShareAction() {
    super("Share project on GitHub", "Easily share project on GitHub", GithubIcons.Github_icon);
  }

  public void update(AnActionEvent e) {
    final Project project = e.getData(PlatformDataKeys.PROJECT);
    if (project == null || project.isDefault()){
      setVisibleEnabled(e, false, false);
      return;
    }
    setVisibleEnabled(e, true, true);
  }

  // get gitRepository
  // check for existing git repo
  // check available repos (net)
  // check privateRepo access (net)
  // Show dialog (window)
  // create GitHub repo (net)
  // create local git repo
  // add GitHub as a remote host
  // make first commit
  // push everything (net)
  @Override
  public void actionPerformed(final AnActionEvent e) {
    // get gitRepository
    final Project project = e.getData(PlatformDataKeys.PROJECT);
    if (project == null || !GithubUtil.testGitExecutable(project)) {
      return;
    }

    BasicAction.saveAll();

    final VirtualFile root = project.getBaseDir();
    final GitRepositoryManager manager = GitUtil.getRepositoryManager(project);
    final GitRepository gitRepository = manager.getRepositoryForFile(root);
    final boolean gitDetected = gitRepository != null;

    // check for existing git repo
    boolean externalRemoteDetected = false;
    if (gitDetected) {
      if (GithubUtil.isRepositoryOnGitHub(gitRepository)) {
        showNotificationWithLink(project, "Project is already on GitHub", "GitHub", StringUtil.notNullize(GithubUtil.findGithubRemoteUrl(gitRepository)));
        return;
      }
      else {
        externalRemoteDetected = !gitRepository.getRemotes().isEmpty();
      }
    }

    // get available GitHub repos with modal progress
    final Ref<HashSet<String>> repoNamesRef = new Ref<HashSet<String>>();
    final Ref<GithubUser> userInfoRef = new Ref<GithubUser>();
    final Ref<GithubAuthData> authRef = new Ref<GithubAuthData>();
    final Ref<IOException> exceptionRef = new Ref<IOException>();
    ProgressManager.getInstance().run(new Task.Modal(project, "Access to GitHub", true) {
      public void run(@NotNull ProgressIndicator indicator) {
        try {
          // get existing github repos (network) and validate auth data
          final Ref<List<RepositoryInfo>> availableReposRef = new Ref<List<RepositoryInfo>>();
          final GithubAuthData auth =
            GithubUtil.runAndGetValidAuth(project, indicator, new ThrowableConsumer<GithubAuthData, IOException>() {
              @Override
              public void consume(GithubAuthData authData) throws IOException {
                availableReposRef.set(GithubUtil.getAvailableRepos(authData));
              }
            });
          if (auth == null || availableReposRef.isNull()) {
            return;
          }
          final HashSet<String> names = new HashSet<String>();
          for (RepositoryInfo info : availableReposRef.get()) {
            names.add(info.getName());
          }
          repoNamesRef.set(names);

          // check access to private repos (network)
          final GithubUser userInfo = GithubUtil.getCurrentUserInfo(auth);
          if (userInfo == null) {
            return;
          }
          userInfoRef.set(userInfo);
          authRef.set(auth);
        }
        catch (IOException e) {
          exceptionRef.set(e);
        }
      }
    });
    if (!exceptionRef.isNull()) {
      Messages.showErrorDialog(exceptionRef.get().getMessage(), "Failed to connect to GitHub");
      return;
    }
    if (repoNamesRef.isNull() || userInfoRef.isNull()) {
      return;
    }

    // Show dialog (window)
    final GithubShareDialog shareDialog =
      new GithubShareDialog(project, repoNamesRef.get(), userInfoRef.get().getPlan().isPrivateRepoAllowed());
    shareDialog.show();
    if (!shareDialog.isOK()) {
      return;
    }
    final boolean isPrivate = shareDialog.isPrivate();
    final String name = shareDialog.getRepositoryName();
    final String description = shareDialog.getDescription();

    // finish the job in background
    final boolean finalExternalRemoteDetected = externalRemoteDetected;
    new Task.Backgroundable(project, "Sharing project on GitHub") {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        try {
          // create GitHub repo (network)
          LOG.info("Creating GitHub repository");
          indicator.setText("Creating GitHub repository");
          if (createGithubRepository(authRef.get(), name, description, isPrivate)) {
            LOG.info("Successfully created GitHub repository");
          }
          else {
            showErrorDialog(project, "Failed to create new GitHub repository", "Create GitHub Repository", indicator);
            return;
          }

          LOG.info("Binding local project with GitHub");
          // creating empty git repo if git is not initialized
          if (!gitDetected) {
            if (!createEmptyGitRepository(project, root, indicator)) {
              return;
            }
          }

          GitRepositoryManager repositoryManager = ServiceManager.getService(project, GitRepositoryManager.class);
          final GitRepository repository = repositoryManager.getRepositoryForRoot(root);
          LOG.assertTrue(repository != null, "GitRepository is null for root " + root);

          //git remote add origin git@github.com:login/name.git
          LOG.info("Adding GitHub as a remote host");
          indicator.setText("Adding GitHub as a remote host");
          final GitSimpleHandler addRemoteHandler = new GitSimpleHandler(project, root, GitCommand.REMOTE);
          addRemoteHandler.setSilent(true);
          final String remoteUrl = GithubApiUtil.getGitHost() + "/" + userInfoRef.get().getLogin() + "/" + name + ".git";
          final String remoteName = finalExternalRemoteDetected ? "github" : "origin";
          addRemoteHandler.addParameters("add", remoteName, remoteUrl);
          try {
            addRemoteHandler.run();
            repository.update();
            if (addRemoteHandler.getExitCode() != 0) {
              showErrorDialog("Failed to add GitHub repository as remote", "Failed to add GitHub repository as remote", indicator);
              return;
            }
          }
          catch (VcsException e) {
            showErrorDialog(e.getMessage(), "Failed to add GitHub repository as remote", indicator);
            LOG.info("Failed to add GitHub as remote: " + e.getMessage());
            return;
          }

          // In this case we should create sample commit for binding project
          if (!performFirstCommitIfRequired(project, root, indicator)) {
            return;
          }

          //git push origin master
          LOG.info("Pushing to github master");
          indicator.setText("Pushing to github master");
          Git git = ServiceManager.getService(Git.class);
          GitCommandResult result = git.push(repository, remoteName, remoteUrl, "refs/heads/master:refs/heads/master");
          if (result.success()) {
            Notificator.getInstance(project).notify(
              new Notification(GithubUtil.GITHUB_NOTIFICATION_GROUP, "Success", "Successfully created project '" + name + "' on GitHub",
                               NotificationType.INFORMATION));
          }
          else {
            Notification notification = new Notification(GithubUtil.GITHUB_NOTIFICATION_GROUP, "Push to GitHub failed",
                                                         "Push failed: <br/>" + result.getErrorOutputAsHtmlString(),
                                                         NotificationType.ERROR);
            Notificator.getInstance(project).notify(notification);
          }
        }
        catch (IOException e) {
          exceptionRef.set(e);
        }
      }
    }.queue();
    if (!exceptionRef.isNull()) {
      Messages.showErrorDialog(exceptionRef.get().getMessage(), "Failed to create new GitHub repository");
    }
  }

  private static boolean createGithubRepository(@NotNull GithubAuthData auth,
                                                @NotNull String name,
                                                @NotNull String description,
                                                boolean aPrivate) throws IOException {
    String path = "/user/repos";
    String requestBody = prepareRequest(name, description, aPrivate);
    JsonElement result = GithubApiUtil.postRequest(auth, path, requestBody);
    if (result == null) {
      return false;
    }
    if (!result.isJsonObject()) {
      LOG.error(String.format("Unexpected JSON result format: %s", result));
      return false;
    }
    return result.getAsJsonObject().has("url");
  }

  private static boolean createEmptyGitRepository(@NotNull Project project,
                                                  @NotNull VirtualFile root,
                                                  @NotNull ProgressIndicator indicator) {
    LOG.info("No git detected, creating empty git repo");
    indicator.setText("Creating empty git repo");
    final GitLineHandler h = new GitLineHandler(project, root, GitCommand.INIT);
    //GitHandlerUtil.doSynchronously(h, GitBundle.getString("initializing.title"), h.printableCommandLine());
    GitHandlerUtil.runInCurrentThread(h, indicator, true, GitBundle.getString("initializing.title"));
    if (!h.errors().isEmpty()) {
      GitUIUtil.showOperationErrors(project, h.errors(), "git init");
      LOG.info("Failed to create empty git repo: " + h.errors());
      return false;
    }
    GitInit.refreshAndConfigureVcsMappings(project, root, "");
    return true;
  }

  private static String prepareRequest(String name, String description, boolean isPrivate) {
    JsonObject json = new JsonObject();
    json.addProperty("name", name);
    json.addProperty("description", description);
    json.addProperty("public", Boolean.toString(!isPrivate));
    return json.toString();

  }

  // check if there is no commits
  // ask for files to add
  // commit
  private static boolean performFirstCommitIfRequired(@NotNull Project project,
                                               @NotNull final VirtualFile root,
                                               @NotNull ProgressIndicator indicator) {
    // get repository
    final GitVcs gitVcs = GitVcs.getInstance(project);
    if (gitVcs == null){
      showErrorDialog(project, "Cannot find git initialized", "Failed to share", indicator);
      return false;
    }

    GitRepositoryManager repositoryManager = ServiceManager.getService(project, GitRepositoryManager.class);
    Git git = ServiceManager.getService(Git.class);
    if (repositoryManager == null || git == null) {
      return false;
    }
    GitRepository repository = repositoryManager.getRepositoryForRoot(root);
    if (repository == null) {
      showErrorDialog(project, "Cannot find git repository for root " + root, "Failed to share", indicator);
      return false;
    }
    if (!repository.isFresh()) {
      return true;
    }

    // commit
    LOG.info("Trying to commit");
    indicator.setText("Trying to commit");
    try {
      LOG.info("Adding files for commit");
      indicator.setText("Adding files to git");

      List<VirtualFile> untrackedFiles = new ArrayList<VirtualFile>(repository.getUntrackedFilesHolder().retrieveUntrackedFiles());
      final GithubUntrackedFilesDialog dialog = new GithubUntrackedFilesDialog(project, untrackedFiles);
      ApplicationManager.getApplication().invokeAndWait(new Runnable() {
        @Override
        public void run() {
          dialog.show();
        }
      }, indicator.getModalityState());
      final Collection<VirtualFile> files2add = dialog.getSelectedFiles();
      if (!dialog.isOK() || files2add.isEmpty()) {
        showErrorDialog(project, "No files to commit", "Failed to commit file during post activities", indicator);
        return false;
      }
      GitFileUtils.addFiles(project, root, files2add);

      LOG.info("Performing commit");
      indicator.setText("Performing commit");
      GitSimpleHandler handler = new GitSimpleHandler(project, root, GitCommand.COMMIT);
      handler.addParameters("-m", "First commit");
      handler.endOptions();
      handler.run();
    }
    catch (VcsException e) {
      LOG.info("Failed to perform initial commit");
      showErrorDialog(project, e.getMessage(), "Failed to commit file during post activities", indicator);
      return false;
    }
    return true;
  }

  private static void showErrorDialog(final String message, final String title, ProgressIndicator indicator) {
    ApplicationManager.getApplication().invokeAndWait(new Runnable() {
      @Override
      public void run() {
        Messages.showErrorDialog(message, title);
      }
    }, indicator.getModalityState());
  }

  private static void showErrorDialog(final Project project, final String message, final String title, ProgressIndicator indicator) {
    ApplicationManager.getApplication().invokeAndWait(new Runnable() {
      @Override
      public void run() {
        Messages.showErrorDialog(project, message, title);
      }
    }, indicator.getModalityState());
  }

  private static void showNotificationWithLink(@NotNull Project project,
                                               @NotNull String message,
                                               @NotNull String title,
                                               @NotNull final String url) {
    Notification notification = new Notification(GithubUtil.GITHUB_NOTIFICATION_GROUP, message, "<a href='" + url + "'>" + title + "</a>",
                                                 NotificationType.INFORMATION, NotificationListener.URL_OPENING_LISTENER);
    Notificator.getInstance(project).notify(notification);
  }

  private static class GithubUntrackedFilesDialog extends SelectFilesDialog {

    public GithubUntrackedFilesDialog(@NotNull Project project, @NotNull List<VirtualFile> untrackedFiles) {
      super(project, untrackedFiles, "Add files to Git", VcsShowConfirmationOption.STATIC_SHOW_CONFIRMATION, true, false, false);
      init();
    }
  }
}
