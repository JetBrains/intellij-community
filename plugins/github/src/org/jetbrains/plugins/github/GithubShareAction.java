package org.jetbrains.plugins.github;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.VcsOutgoingChangesProvider;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.ChangeListManagerImpl;
import com.intellij.openapi.vcs.changes.InvokeAfterUpdateMode;
import com.intellij.openapi.vcs.changes.actions.RefreshAction;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.concurrency.Semaphore;
import com.intellij.util.containers.HashSet;
import git4idea.GitDeprecatedRemote;
import git4idea.GitUtil;
import git4idea.GitVcs;
import git4idea.actions.BasicAction;
import git4idea.actions.GitInit;
import git4idea.commands.*;
import git4idea.i18n.GitBundle;
import git4idea.push.GitPushUtils;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import git4idea.util.GitFileUtils;
import git4idea.util.GitUIUtil;
import org.jetbrains.plugins.github.ui.GithubShareDialog;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author oleg
 */
public class GithubShareAction extends DumbAwareAction {
  private static final Logger LOG = Logger.getInstance(GithubShareAction.class.getName());

  public GithubShareAction() {
    super("Share project on GitHub", "Easily share project on GitHub", GithubUtil.GITHUB_ICON);
  }

  public void update(AnActionEvent e) {
    final Project project = e.getData(PlatformDataKeys.PROJECT);
    if (project == null || project.isDefault()){
      e.getPresentation().setEnabled(false);
      e.getPresentation().setVisible(false);
      return;
    }
    e.getPresentation().setVisible(true);
    e.getPresentation().setEnabled(true);
  }

  @Override
  public void actionPerformed(final AnActionEvent e) {
    final Project project = e.getData(PlatformDataKeys.PROJECT);
    if (!GithubUtil.testGitExecutable(project)){
      return;
    }
    final VirtualFile root = project.getBaseDir();
    // Check if git is already initialized and presence of remote branch
    final boolean gitDetected = GitUtil.isUnderGit(root);
    if (gitDetected) {
      try {
        final List<GitDeprecatedRemote> gitRemotes = GitDeprecatedRemote.list(project, root);
        if (!gitRemotes.isEmpty()) {
          Messages.showErrorDialog(project, "Project is already under git with configured remote", "Cannot create new GitHub repository");
          return;
        }
      }
      catch (VcsException e2) {
        Messages.showErrorDialog(project, "Error happened during git operation: " + e2.getMessage(), "Cannot create new GitHub repository");
        return;
      }
    }

    BasicAction.saveAll();
    final List<RepositoryInfo> availableRepos = GithubUtil.getAvailableRepos(project, true);
    if (availableRepos == null){
      return;
    }
    final HashSet<String> names = new HashSet<String>();
    for (RepositoryInfo info : availableRepos) {
      names.add(info.getName());
    }

    final GithubSettings settings = GithubSettings.getInstance();
    final String password = settings.getPassword();
    final Boolean privateRepoAllowed = GithubUtil.accessToGithubWithModalProgress(project, new Computable<Boolean>() {
      @Override
      public Boolean compute() {
        ProgressManager.getInstance().getProgressIndicator().setText("Trying to login to GitHub");
        return GithubUtil.isPrivateRepoAllowed(settings.getHost(), settings.getLogin(), password);
      }
    });
    if (privateRepoAllowed == null) {
      return;
    }
    final GithubShareDialog shareDialog = new GithubShareDialog(project, names, privateRepoAllowed);
    shareDialog.show();
    if (!shareDialog.isOK()) {
      return;
    }

    final boolean isPrivate = shareDialog.isPrivate();
    final String name = shareDialog.getRepositoryName();
    final String description = shareDialog.getDescription();
    try {
      LOG.info("Creating GitHub repository");
      final String escapedDescription = JDOMUtil.escapeText(description, true, true).replace("&#", "%");
      GithubUtil.doREST(settings.getHost(), settings.getLogin(), settings.getPassword(),
                        "/repos/create?name=" + name + "&public=" + (isPrivate ? "0" : "1") + "&description=" + escapedDescription, true).releaseConnection();
      LOG.info("Successfully created GitHub repository");
    }
    catch (final Exception e1) {
      Messages.showErrorDialog(e1.getMessage(), "Failed to create new GitHub repository");
      return;
    }
    if (bindToGithub(project, root, gitDetected, settings.getLogin(), name)) {
      Notifications.Bus.notify(new Notification("github", "Success", "Successfully created project ''" + name + "'' on github",
                                                NotificationType.INFORMATION));
    }
  }

  private boolean bindToGithub(final Project project, final VirtualFile root, final boolean gitDetected, final String login, String name) {
    LOG.info("Binding local project with GitHub");
    // creating empty git repo if git isnot initialized
    if (!gitDetected) {
      LOG.info("No git detected, creating empty git repo");
      final GitLineHandler h = new GitLineHandler(project, root, GitCommand.INIT);
      h.setNoSSH(true);
      GitHandlerUtil.doSynchronously(h, GitBundle.getString("initializing.title"), h.printableCommandLine());
      if (!h.errors().isEmpty()) {
        GitUIUtil.showOperationErrors(project, h.errors(), "git init");
        LOG.info("Failed to create empty git repo: " + h.errors());
        return false;
      }
      final ProgressManager manager = ProgressManager.getInstance();
      manager.runProcessWithProgressSynchronously(new Runnable() {
        @Override
        public void run() {
          GitInit.refreshAndConfigureVcsMappings(project, root, "");
        }
      }, "Committing", false, project);
    }

    // In this case we should create sample commit for binding project
    if (!performFirstCommitIfRequired(project, root)) {
      return false;
    }

    //git remote add origin git@github.com:login/name.git
    LOG.info("Adding GitHub as a remote host");
    final GitSimpleHandler addRemoteHandler = new GitSimpleHandler(project, root, GitCommand.REMOTE);
    addRemoteHandler.setNoSSH(true);
    addRemoteHandler.setSilent(true);
    addRemoteHandler.addParameters("add", "origin", "git@github.com:" + login + "/" + name + ".git");
    try {
      addRemoteHandler.run();
      if (addRemoteHandler.getExitCode() != 0) {
        Messages.showErrorDialog("Failed to add GitHub repository as remote", "Failed to add GitHub repository as remote");
        return false;
      }
    }
    catch (VcsException e) {
      Messages.showErrorDialog(e.getMessage(), "Failed to add GitHub repository as remote");
      LOG.info("Failed to add GitHub as remote: " + e.getMessage());
      return false;
    }

    //git push origin master
    final ProgressManager manager = ProgressManager.getInstance();
    final ArrayList<VcsException> errors = new ArrayList<VcsException>();
    manager.runProcessWithProgressSynchronously(new Runnable() {
      public void run() {
        final ProgressIndicator progressIndicator = manager.getProgressIndicator();
        if (progressIndicator != null){
            progressIndicator.setText("Pushing to GitHub");
          }
          final GitLineHandler gitPushHandler = new GitLineHandler(project, root, GitCommand.PUSH);
          gitPushHandler.addParameters("-u", "origin", "master");
          GitPushUtils.trackPushRejectedAsError(gitPushHandler, "Rejected push (" + root.getPresentableUrl() + "): ");
          errors.addAll(GitHandlerUtil.doSynchronouslyWithExceptions(gitPushHandler));
        }
      }, GitBundle.getString("push.active.pushing"), false, project);
      if (!errors.isEmpty()) {
        GitUIUtil.showOperationErrors(project, errors, GitBundle.getString("push.active.pushing"));
    }
    // refresh vcs manually
    RefreshAction.doRefresh(project);
    return true;
  }

  private boolean performFirstCommitIfRequired(final Project project, final VirtualFile root) {
    final GitVcs gitVcs = GitVcs.getInstance(project);
    if (gitVcs == null){
      Messages.showErrorDialog(project, "Cannot find git initialized", "Failed to share");
      return false;
    }
    final VcsOutgoingChangesProvider<CommittedChangeList> provider = gitVcs.getOutgoingChangesProvider();
    if (provider == null) {
      Messages.showErrorDialog(project, "Cannot find git initialized", "Failed to share");
      return false;
    }

    GitRepositoryManager repositoryManager = ServiceManager.getService(project, GitRepositoryManager.class);
    Git git = ServiceManager.getService(Git.class);
    if (repositoryManager == null || git == null) {
      return false;
    }
    GitRepository repository = repositoryManager.getRepositoryForRoot(root);
    if (repository == null) {
      Messages.showErrorDialog(project, "Cannot find git repository for root " + root, "Failed to share");
      return false;
    }
    if (!repository.isFresh()) {
      return true;
    }

    final Ref<Exception> exceptionRef = new Ref<Exception>();
    // Creating or modifying readme file
    LOG.info("Touching file 'README' for initial commit");
    final VirtualFile readmeFile = ApplicationManager.getApplication().runWriteAction(new Computable<VirtualFile>() {
      @Override
      public VirtualFile compute() {
        VirtualFile file = null;
        try {
          file = root.findChild("README");
          if (file == null) {
            file = root.createChildData(this, "README");
            VfsUtil.saveText(file, "This file was created by " + ApplicationInfoEx.getInstanceEx().getFullApplicationName() + " for binding GitHub repository");
          } else {
            VfsUtil.saveText(file, VfsUtil.loadText(file) + "\nThis file was modified by " + ApplicationInfoEx.getInstanceEx().getFullApplicationName() + " for binding GitHub repository");
          }
        }
        catch (IOException e) {
          exceptionRef.set(e);
          LOG.info("Failed to touch file 'README' for initial commit: " + e.getMessage());
        }
        return file;
      }
    });
    if (!exceptionRef.isNull()) {
      Messages.showErrorDialog(project, exceptionRef.get().getMessage(), "Failed to modify file during post activities");
    }
    exceptionRef.set(null);
    LOG.info("Trying to commit");
    final ProgressManager manager = ProgressManager.getInstance();
    manager.runProcessWithProgressSynchronously(new Runnable() {
      public void run() {
        try {
          LOG.info("Adding files for commit");
          final ProgressIndicator progressIndicator = manager.getProgressIndicator();
          if (progressIndicator != null){
            progressIndicator.setText("Adding files to git");
          }
          // Add readme files to git
          final ArrayList<VirtualFile> files2Add = new ArrayList<VirtualFile>();
          if (readmeFile != null) {
            files2Add.add(readmeFile);
          }
          final ChangeListManagerImpl changeListManager = (ChangeListManagerImpl)ChangeListManager.getInstance(project);

          // Force update
          final Semaphore semaphore = new Semaphore();
          semaphore.up();
          changeListManager.invokeAfterUpdate(new Runnable() {
            @Override
            public void run() {
              semaphore.down();
            }
          }, InvokeAfterUpdateMode.SILENT, null, null);
          if (!semaphore.waitFor(30000)) {
            exceptionRef.set(new VcsException("Too long VCS update"));
            return;
          }

          for (VirtualFile file : changeListManager.getUnversionedFiles()) {
            if (file.getPath().contains(".idea")) {
              continue;
            }
            if (readmeFile != null && readmeFile.equals(file)) {
              continue;
            }
            files2Add.add(file);
          }
          if (progressIndicator != null){
            progressIndicator.setText("Adding files to git");
          }
          GitFileUtils.addFiles(project, root, files2Add);

          LOG.info("Performing commit");
          if (progressIndicator != null){
            progressIndicator.setText("Performing commit");
          }
          GitSimpleHandler handler = new GitSimpleHandler(project, root, GitCommand.COMMIT);
          handler.addParameters("-m", "First commit");
          handler.setNoSSH(true);
          handler.endOptions();
          handler.run();
        }
        catch (VcsException e) {
          exceptionRef.set(e);
          LOG.info("Failed to commit to GitHub");
        }
      }
    }, "Performing post creating github repository activities", true, project);

    if (!exceptionRef.isNull()) {
      Messages.showErrorDialog(project, exceptionRef.get().getMessage(), "Failed to commit file during post activities");
      return false;
    }
    return true;
  }
}
