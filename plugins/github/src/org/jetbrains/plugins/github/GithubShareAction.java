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

import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.ui.SelectFilesDialog;
import com.intellij.openapi.vcs.ui.CommitMessage;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashSet;
import com.intellij.vcsUtil.VcsFileUtil;
import git4idea.DialogManager;
import git4idea.GitLocalBranch;
import git4idea.GitUtil;
import git4idea.actions.BasicAction;
import git4idea.actions.GitInit;
import git4idea.commands.*;
import git4idea.i18n.GitBundle;
import git4idea.repo.GitRemote;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import git4idea.util.GitFileUtils;
import git4idea.util.GitUIUtil;
import icons.GithubIcons;
import org.apache.http.HttpStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.github.api.GithubApiUtil;
import org.jetbrains.plugins.github.api.GithubFullPath;
import org.jetbrains.plugins.github.api.data.GithubRepo;
import org.jetbrains.plugins.github.api.data.GithubUserDetailed;
import org.jetbrains.plugins.github.exceptions.GithubStatusCodeException;
import org.jetbrains.plugins.github.ui.GithubShareDialog;
import org.jetbrains.plugins.github.util.GithubAuthDataHolder;
import org.jetbrains.plugins.github.util.GithubNotifications;
import org.jetbrains.plugins.github.util.GithubUrlUtil;
import org.jetbrains.plugins.github.util.GithubUtil;

import javax.swing.*;
import java.io.IOException;
import java.util.*;

/**
 * @author oleg
 */
public class GithubShareAction extends DumbAwareAction {
  private static final Logger LOG = GithubUtil.LOG;

  public GithubShareAction() {
    super("Share Project on GitHub", "Easily share project on GitHub", GithubIcons.Github_icon);
  }

  public void update(AnActionEvent e) {
    final Project project = e.getData(CommonDataKeys.PROJECT);
    if (project == null || project.isDefault()) {
      e.getPresentation().setEnabledAndVisible(false);
      return;
    }
    e.getPresentation().setEnabledAndVisible(true);
  }

  // get gitRepository
  // check for existing git repo
  // check available repos and privateRepo access (net)
  // Show dialog (window)
  // create GitHub repo (net)
  // create local git repo (if not exist)
  // add GitHub as a remote host
  // make first commit
  // push everything (net)
  @Override
  public void actionPerformed(final AnActionEvent e) {
    final Project project = e.getData(CommonDataKeys.PROJECT);
    final VirtualFile file = e.getData(CommonDataKeys.VIRTUAL_FILE);

    if (project == null || project.isDisposed() || !GithubUtil.testGitExecutable(project)) {
      return;
    }

    shareProjectOnGithub(project, file);
  }

  public static void shareProjectOnGithub(@NotNull final Project project, @Nullable final VirtualFile file) {
    BasicAction.saveAll();

    // get gitRepository
    final GitRepository gitRepository = GithubUtil.getGitRepository(project, file);
    final boolean gitDetected = gitRepository != null;
    final VirtualFile root = gitDetected ? gitRepository.getRoot() : project.getBaseDir();

    final GithubAuthDataHolder authHolder = GithubAuthDataHolder.createFromSettings();

    // check for existing git repo
    Set<String> existingRemotes = Collections.emptySet();
    if (gitDetected) {
      final String githubRemote = GithubUtil.findGithubRemoteUrl(gitRepository);
      if (githubRemote != null) {
        if (!checkExistingRemote(project, authHolder, githubRemote)) return;
      }

      existingRemotes = ContainerUtil.map2Set(gitRepository.getRemotes(), GitRemote::getName);
    }

    // get available GitHub repos with modal progress
    final GithubInfo githubInfo = loadGithubInfoWithModal(authHolder, project);
    if (githubInfo == null) {
      return;
    }

    // Show dialog (window)
    final GithubShareDialog shareDialog =
      new GithubShareDialog(project, githubInfo.getRepositoryNames(), existingRemotes, githubInfo.getUser().canCreatePrivateRepo());
    DialogManager.show(shareDialog);
    if (!shareDialog.isOK()) {
      return;
    }
    final boolean isPrivate = shareDialog.isPrivate();
    final String name = shareDialog.getRepositoryName();
    final String description = shareDialog.getDescription();
    final String remoteName = shareDialog.getRemoteName();

    new Task.Backgroundable(project, "Sharing Project on GitHub...") {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        // create GitHub repo (network)
        LOG.info("Creating GitHub repository");
        indicator.setText("Creating GitHub repository...");
        final String url = createGithubRepository(project, authHolder, indicator, name, description, isPrivate);
        if (url == null) {
          return;
        }
        LOG.info("Successfully created GitHub repository");

        // creating empty git repo if git is not initialized
        LOG.info("Binding local project with GitHub");
        if (!gitDetected) {
          LOG.info("No git detected, creating empty git repo");
          indicator.setText("Creating empty git repo...");
          if (!createEmptyGitRepository(project, root, indicator)) {
            return;
          }
        }

        GitRepositoryManager repositoryManager = GitUtil.getRepositoryManager(project);
        final GitRepository repository = repositoryManager.getRepositoryForRoot(root);
        if (repository == null) {
          GithubNotifications.showError(project, "Failed to create GitHub Repository", "Can't find Git repository");
          return;
        }

        final String remoteUrl = GithubUrlUtil.getCloneUrl(githubInfo.getUser().getLogin(), name);

        //git remote add origin git@github.com:login/name.git
        LOG.info("Adding GitHub as a remote host");
        indicator.setText("Adding GitHub as a remote host...");
        if (!GithubUtil.addGithubRemote(project, repository, remoteName, remoteUrl)) {
          return;
        }

        // create sample commit for binding project
        if (!performFirstCommitIfRequired(project, root, repository, indicator, name, url)) {
          return;
        }

        //git push origin master
        LOG.info("Pushing to github master");
        indicator.setText("Pushing to github master...");
        if (!pushCurrentBranch(project, repository, remoteName, remoteUrl, name, url)) {
          return;
        }

        GithubNotifications.showInfoURL(project, "Successfully shared project on GitHub", name, url);
      }
    }.queue();
  }

  private static boolean checkExistingRemote(@NotNull final Project project,
                                             @NotNull final GithubAuthDataHolder authHolder,
                                             @NotNull String remote) {
    final GithubFullPath path = GithubUrlUtil.getUserAndRepositoryFromRemoteUrl(remote);
    if (path == null) {
      return GithubNotifications.showYesNoDialog(project,
                                                 "Project Is Already on GitHub",
                                                 "Can't connect to repository from configured remote. You could want to check .git config.\n" +
                                                 "Do you want to proceed anyway?");
    }

    try {
      GithubRepo repo = GithubUtil.computeValueInModalIO(project, "Access to GitHub", indicator ->
        GithubUtil.runTask(project, authHolder, indicator, connection ->
          GithubApiUtil.getDetailedRepoInfo(connection, path.getUser(), path.getRepository())));
      int result = Messages.showDialog(project,
                                       "Successfully connected to " + repo.getHtmlUrl() + ".\n" +
                                       "Do you want to proceed anyway?",
                                       "Project Is Already on GitHub",
                                       new String[]{"Continue", "Open in Browser", Messages.CANCEL_BUTTON}, 2, Messages.getQuestionIcon());
      if (result == 0) return true;
      if (result == 1) {
        BrowserUtil.browse(repo.getHtmlUrl());
      }
      return false;
    }
    catch (GithubStatusCodeException e) {
      if (e.getStatusCode() == HttpStatus.SC_NOT_FOUND) {
        return GithubNotifications.showYesNoDialog(project,
                                                   "Project Is Already on GitHub",
                                                   "Can't connect to repository from configured remote. You could want to check .git config.\n" +
                                                   "Do you want to proceed anyway?");
      }

      GithubNotifications.showErrorDialog(project, "Failed to Connect to GitHub", e);
      return false;
    }
    catch (IOException e) {
      GithubNotifications.showErrorDialog(project, "Failed to Connect to GitHub", e);
      return false;
    }
  }

  @Nullable
  private static GithubInfo loadGithubInfoWithModal(@NotNull final GithubAuthDataHolder authHolder, @NotNull final Project project) {
    try {
      return GithubUtil.computeValueInModalIO(project, "Access to GitHub", indicator -> {
        // get existing github repos (network) and validate auth data
        return GithubUtil.runTask(project, authHolder, indicator, connection -> {
          // check access to private repos (network)
          GithubUserDetailed userInfo = GithubApiUtil.getCurrentUser(connection);

          HashSet<String> names = new HashSet<>();
          for (GithubRepo info : GithubApiUtil.getUserRepos(connection)) {
            names.add(info.getName());
          }
          return new GithubInfo(userInfo, names);
        });
      });
    }
    catch (IOException e) {
      GithubNotifications.showErrorDialog(project, "Failed to Connect to GitHub", e);
      return null;
    }
  }

  @Nullable
  private static String createGithubRepository(@NotNull Project project,
                                               @NotNull GithubAuthDataHolder authHolder,
                                               @NotNull ProgressIndicator indicator,
                                               @NotNull final String name,
                                               @NotNull final String description,
                                               final boolean isPrivate) {

    try {
      return GithubUtil.runTask(project, authHolder, indicator, connection ->
        GithubApiUtil.createRepo(connection, name, description, isPrivate)).getHtmlUrl();
    }
    catch (IOException e) {
      GithubNotifications.showError(project, "Failed to create GitHub Repository", e);
      return null;
    }
  }

  private static boolean createEmptyGitRepository(@NotNull Project project,
                                                  @NotNull VirtualFile root,
                                                  @NotNull ProgressIndicator indicator) {
    final GitLineHandler h = new GitLineHandler(project, root, GitCommand.INIT);
    h.setStdoutSuppressed(false);
    GitHandlerUtil.runInCurrentThread(h, indicator, true, GitBundle.getString("initializing.title"));
    if (!h.errors().isEmpty()) {
      GitUIUtil.showOperationErrors(project, h.errors(), "git init");
      LOG.info("Failed to create empty git repo: " + h.errors());
      return false;
    }
    GitInit.refreshAndConfigureVcsMappings(project, root, root.getPath());
    return true;
  }

  private static boolean performFirstCommitIfRequired(@NotNull final Project project,
                                                      @NotNull VirtualFile root,
                                                      @NotNull GitRepository repository,
                                                      @NotNull ProgressIndicator indicator,
                                                      @NotNull String name,
                                                      @NotNull String url) {
    // check if there is no commits
    if (!repository.isFresh()) {
      return true;
    }

    LOG.info("Trying to commit");
    try {
      LOG.info("Adding files for commit");
      indicator.setText("Adding files to git...");

      // ask for files to add
      final List<VirtualFile> trackedFiles = ChangeListManager.getInstance(project).getAffectedFiles();
      final Collection<VirtualFile> untrackedFiles =
        filterOutIgnored(project, repository.getUntrackedFilesHolder().retrieveUntrackedFiles());
      trackedFiles.removeAll(untrackedFiles); // fix IDEA-119855

      final List<VirtualFile> allFiles = new ArrayList<>();
      allFiles.addAll(trackedFiles);
      allFiles.addAll(untrackedFiles);

      final Ref<GithubUntrackedFilesDialog> dialogRef = new Ref<>();
      ApplicationManager.getApplication().invokeAndWait(() -> {
        GithubUntrackedFilesDialog dialog = new GithubUntrackedFilesDialog(project, allFiles);
        if (!trackedFiles.isEmpty()) {
          dialog.setSelectedFiles(trackedFiles);
        }
        DialogManager.show(dialog);
        dialogRef.set(dialog);
      }, indicator.getModalityState());
      final GithubUntrackedFilesDialog dialog = dialogRef.get();

      final Collection<VirtualFile> files2commit = dialog.getSelectedFiles();
      if (!dialog.isOK() || files2commit.isEmpty()) {
        GithubNotifications.showInfoURL(project, "Successfully created empty repository on GitHub", name, url);
        return false;
      }

      Collection<VirtualFile> files2add = ContainerUtil.intersection(untrackedFiles, files2commit);
      Collection<VirtualFile> files2rm = ContainerUtil.subtract(trackedFiles, files2commit);
      Collection<VirtualFile> modified = new HashSet<>(trackedFiles);
      modified.addAll(files2commit);

      GitFileUtils.addFiles(project, root, files2add);
      GitFileUtils.deleteFilesFromCache(project, root, files2rm);

      // commit
      LOG.info("Performing commit");
      indicator.setText("Performing commit...");
      GitSimpleHandler handler = new GitSimpleHandler(project, root, GitCommand.COMMIT);
      handler.setStdoutSuppressed(false);
      handler.addParameters("-m", dialog.getCommitMessage());
      handler.endOptions();
      handler.run();

      VcsFileUtil.markFilesDirty(project, modified);
    }
    catch (VcsException e) {
      LOG.warn(e);
      GithubNotifications.showErrorURL(project, "Can't finish GitHub sharing process", "Successfully created project ", "'" + name + "'",
                                       " on GitHub, but initial commit failed:<br/>" + GithubUtil.getErrorTextFromException(e), url);
      return false;
    }
    LOG.info("Successfully created initial commit");
    return true;
  }

  @NotNull
  private static Collection<VirtualFile> filterOutIgnored(@NotNull Project project, @NotNull Collection<VirtualFile> files) {
    final ChangeListManager changeListManager = ChangeListManager.getInstance(project);
    final ProjectLevelVcsManager vcsManager = ProjectLevelVcsManager.getInstance(project);
    return ContainerUtil.filter(files, file -> !changeListManager.isIgnoredFile(file) && !vcsManager.isIgnored(file));
  }

  private static boolean pushCurrentBranch(@NotNull Project project,
                                           @NotNull GitRepository repository,
                                           @NotNull String remoteName,
                                           @NotNull String remoteUrl,
                                           @NotNull String name,
                                           @NotNull String url) {
    Git git = ServiceManager.getService(Git.class);

    GitLocalBranch currentBranch = repository.getCurrentBranch();
    if (currentBranch == null) {
      GithubNotifications.showErrorURL(project, "Can't finish GitHub sharing process", "Successfully created project ", "'" + name + "'",
                                       " on GitHub, but initial push failed: no current branch", url);
      return false;
    }
    GitCommandResult result = git.push(repository, remoteName, remoteUrl, currentBranch.getName(), true);
    if (!result.success()) {
      GithubNotifications.showErrorURL(project, "Can't finish GitHub sharing process", "Successfully created project ", "'" + name + "'",
                                       " on GitHub, but initial push failed:<br/>" + result.getErrorOutputAsHtmlString(), url);
      return false;
    }
    return true;
  }

  public static class GithubUntrackedFilesDialog extends SelectFilesDialog implements DataProvider {
    @NotNull private final Project myProject;
    private CommitMessage myCommitMessagePanel;

    public GithubUntrackedFilesDialog(@NotNull Project project, @NotNull List<VirtualFile> untrackedFiles) {
      super(project, untrackedFiles, null, null, true, false, false);
      myProject = project;
      setTitle("Add Files For Initial Commit");
      init();
    }

    @Override
    protected JComponent createNorthPanel() {
      return null;
    }

    @Override
    protected JComponent createCenterPanel() {
      final JComponent tree = super.createCenterPanel();

      myCommitMessagePanel = new CommitMessage(myProject);
      myCommitMessagePanel.setCommitMessage("Initial commit");

      Splitter splitter = new Splitter(true);
      splitter.setHonorComponentsMinimumSize(true);
      splitter.setFirstComponent(tree);
      splitter.setSecondComponent(myCommitMessagePanel);
      splitter.setProportion(0.7f);

      return splitter;
    }

    @NotNull
    public String getCommitMessage() {
      return myCommitMessagePanel.getComment();
    }

    @Nullable
    @Override
    public Object getData(@NonNls String dataId) {
      if (VcsDataKeys.COMMIT_MESSAGE_CONTROL.is(dataId)) {
        return myCommitMessagePanel;
      }
      return null;
    }

    @Override
    protected String getDimensionServiceKey() {
      return "Github.UntrackedFilesDialog";
    }
  }

  private static class GithubInfo {
    @NotNull private final GithubUserDetailed myUser;
    @NotNull private final HashSet<String> myRepositoryNames;

    GithubInfo(@NotNull GithubUserDetailed user, @NotNull HashSet<String> repositoryNames) {
      myUser = user;
      myRepositoryNames = repositoryNames;
    }

    @NotNull
    public GithubUserDetailed getUser() {
      return myUser;
    }

    @NotNull
    public HashSet<String> getRepositoryNames() {
      return myRepositoryNames;
    }
  }
}
