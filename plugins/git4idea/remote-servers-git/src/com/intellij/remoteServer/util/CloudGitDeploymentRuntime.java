/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.remoteServer.util;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vcs.changes.ui.CommitChangeListDialog;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.remoteServer.agent.util.CloudAgentLoggingHandler;
import com.intellij.remoteServer.agent.util.CloudGitApplication;
import com.intellij.remoteServer.configuration.deployment.DeploymentSource;
import com.intellij.remoteServer.runtime.deployment.DeploymentLogManager;
import com.intellij.remoteServer.runtime.deployment.DeploymentTask;
import com.intellij.util.ArrayUtil;
import com.intellij.util.concurrency.Semaphore;
import git4idea.GitUtil;
import git4idea.actions.GitInit;
import git4idea.commands.*;
import git4idea.repo.GitRemote;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import git4idea.util.GitFileUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * @author michael.golubev
 */
public class CloudGitDeploymentRuntime extends CloudDeploymentRuntime {

  private static final Logger LOG = Logger.getInstance(CloudGitDeploymentRuntime.class);

  private static final String COMMIT_MESSAGE = "Deploy";

  private static final CommitSession NO_COMMIT = new CommitSession() {
    @Override
    public void execute(Collection<Change> changes, String commitMessage) {

    }
  };

  private static final List<CommitExecutor> ourCommitExecutors = Arrays.asList(
    new CommitExecutor() {

      @Nls
      @Override
      public String getActionText() {
        return "Commit and Push";
      }

      @NotNull
      @Override
      public CommitSession createCommitSession() {
        return CommitSession.VCS_COMMIT;
      }
    },
    new CommitExecutorBase() {

      @Nls
      @Override
      public String getActionText() {
        return "Push without Commit";
      }

      @NotNull
      @Override
      public CommitSession createCommitSession() {
        return NO_COMMIT;
      }

      @Override
      public boolean areChangesRequired() {
        return false;
      }
    }
  );

  private final GitRepositoryManager myGitRepositoryManager;
  private final Git myGit;

  private final VirtualFile myContentRoot;
  private final File myRepositoryRootFile;

  private final String myDefaultRemoteName;
  private final ChangeListManagerEx myChangeListManager;
  private String myRemoteName;
  private final String myCloudName;

  private GitRepository myRepository;

  public CloudGitDeploymentRuntime(CloudMultiSourceServerRuntimeInstance serverRuntime,
                                   DeploymentSource source,
                                   File repositoryRoot,
                                   DeploymentTask<? extends CloudDeploymentNameConfiguration> task,
                                   DeploymentLogManager logManager,
                                   String defaultRemoteName,
                                   String cloudName) throws ServerRuntimeException {
    super(serverRuntime, source, task, logManager);

    myDefaultRemoteName = defaultRemoteName;
    myCloudName = cloudName;

    myRepositoryRootFile = repositoryRoot;

    VirtualFile contentRoot = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(myRepositoryRootFile);
    LOG.assertTrue(contentRoot != null, "Repository root is not found");
    myContentRoot = contentRoot;

    Project project = getProject();
    myGitRepositoryManager = GitUtil.getRepositoryManager(project);
    myGit = ServiceManager.getService(Git.class);
    if (myGit == null) {
      throw new ServerRuntimeException("Can't initialize GIT");
    }
    myChangeListManager = ChangeListManagerImpl.getInstanceImpl(project);
  }

  @Override
  public CloudGitApplication deploy() throws ServerRuntimeException {
    CloudGitApplication application = findOrCreateApplication();
    deployApplication(application);
    return application;
  }

  private void deployApplication(CloudGitApplication application) throws ServerRuntimeException {
    boolean firstDeploy = findRepository() == null;

    GitRepository repository = findOrCreateRepository();
    addOrResetGitRemote(application, repository);

    final LocalChangeList activeChangeList = myChangeListManager.getDefaultChangeList();

    if (activeChangeList != null && !firstDeploy) {
      commitWithChangesDialog(activeChangeList);
    }
    else {
      add();
      commit();
    }
    repository.update();
    pushApplication(application);
  }

  protected void commitWithChangesDialog(final @NotNull LocalChangeList activeChangeList)
    throws ServerRuntimeException {

    Collection<Change> changes = activeChangeList.getChanges();
    final List<Change> relevantChanges = new ArrayList<>();
    for (Change change : changes) {
      if (isRelevant(change.getBeforeRevision()) || isRelevant(change.getAfterRevision())) {
        relevantChanges.add(change);
      }
    }

    final Semaphore commitSemaphore = new Semaphore();
    commitSemaphore.down();

    final Ref<Boolean> commitSucceeded = new Ref<>(false);
    Boolean commitStarted = runOnEdt(() -> CommitChangeListDialog.commitChanges(getProject(),
                                                                                relevantChanges,
                                                                                activeChangeList,
                                                                                ourCommitExecutors,
                                                                                false,
                                                                                COMMIT_MESSAGE,
                                                                                new CommitResultHandler() {

                                                                                  @Override
                                                                                  public void onSuccess(@NotNull String commitMessage) {
                                                                                    commitSucceeded.set(true);
                                                                                    commitSemaphore.up();
                                                                                  }

                                                                                  @Override
                                                                                  public void onFailure() {
                                                                                    commitSemaphore.up();
                                                                                  }
                                                                                },
                                                                                false));
    if (commitStarted != null && commitStarted) {
      commitSemaphore.waitFor();
      if (!commitSucceeded.get()) {
        getRepository().update();
        throw new ServerRuntimeException("Commit failed");
      }
    }
    else {
      throw new ServerRuntimeException("Deploy interrupted");
    }
  }

  private boolean isRelevant(ContentRevision contentRevision) throws ServerRuntimeException {
    if (contentRevision == null) {
      return false;
    }
    GitRepository repository = getRepository();
    VirtualFile affectedFile = contentRevision.getFile().getVirtualFile();
    return affectedFile != null && VfsUtilCore.isAncestor(repository.getRoot(), affectedFile, false);
  }

  private static <T> T runOnEdt(final Computable<T> computable) {
    final Ref<T> result = new Ref<>();
    ApplicationManager.getApplication().invokeAndWait(() -> result.set(computable.compute()));
    return result.get();
  }

  public boolean isDeployed() throws ServerRuntimeException {
    return findApplication() != null;
  }

  public CloudGitApplication findOrCreateApplication() throws ServerRuntimeException {
    CloudGitApplication application = findApplication();
    if (application == null) {
      application = createApplication();
    }
    return application;
  }

  public void addOrResetGitRemote(CloudGitApplication application, GitRepository repository) throws ServerRuntimeException {
    String gitUrl = application.getGitUrl();
    if (myRemoteName == null) {
      for (GitRemote gitRemote : repository.getRemotes()) {
        if (gitRemote.getUrls().contains(gitUrl)) {
          myRemoteName = gitRemote.getName();
          return;
        }
      }
    }
    GitRemote gitRemote = GitUtil.findRemoteByName(repository, getRemoteName());
    if (gitRemote == null) {
      addGitRemote(application);
    }
    else if (!gitRemote.getUrls().contains(gitUrl)) {
      resetGitRemote(application);
    }
  }

  public GitRepository findOrCreateRepository() throws ServerRuntimeException {
    GitRepository repository = findRepository();
    if (repository == null) {
      getLoggingHandler().println("Initializing git repository...");
      GitCommandResult gitInitResult = getGit().init(getProject(), getRepositoryRoot(), createGitLineHandlerListener());
      checkGitResult(gitInitResult);

      refreshApplicationRepository();

      repository = getRepository();
    }
    return repository;
  }

  public void downloadExistingApplication() throws ServerRuntimeException {
    new CloneJobWithRemote().cloneToModule(getApplication().getGitUrl());
    getRepository().update();
    refreshContentRoot();
  }

  protected Git getGit() {
    return myGit;
  }

  protected VirtualFile getRepositoryRoot() {
    return myContentRoot;
  }

  protected File getRepositoryRootFile() {
    return myRepositoryRootFile;
  }

  protected static void checkGitResult(GitCommandResult commandResult) throws ServerRuntimeException {
    if (!commandResult.success()) {
      throw new ServerRuntimeException(commandResult.getErrorOutputAsJoinedString());
    }
  }

  protected GitLineHandlerListener createGitLineHandlerListener() {
    return new GitLineHandlerAdapter() {

      @Override
      public void onLineAvailable(String line, Key outputType) {
        getLoggingHandler().println(line);
      }
    };
  }

  @Override
  protected CloudAgentLoggingHandler getLoggingHandler() {
    return super.getLoggingHandler();
  }

  protected void addGitRemote(CloudGitApplication application) throws ServerRuntimeException {
    doGitRemote(getRemoteName(), application, "add", CloudBundle.getText("failed.add.remote", getRemoteName()));
  }

  protected void resetGitRemote(CloudGitApplication application) throws ServerRuntimeException {
    doGitRemote(getRemoteName(), application, "set-url", CloudBundle.getText("failed.reset.remote", getRemoteName()));
  }

  protected void doGitRemote(String remoteName,
                             CloudGitApplication application,
                             String subCommand,
                             String failMessage)
    throws ServerRuntimeException {
    try {
      GitLineHandler handler = new GitLineHandler(getProject(), myContentRoot, GitCommand.REMOTE);
      handler.setSilent(false);
      handler.addParameters(subCommand, remoteName, application.getGitUrl());
      GitCommandResult result = myGit.runCommand(handler);
      result.getOutputOrThrow();
      getRepository().update();
      if (result.getExitCode() != 0) {
        throw new ServerRuntimeException(failMessage);
      }
    }
    catch (VcsException e) {
      throw new ServerRuntimeException(e);
    }
  }

  @Nullable
  protected GitRepository findRepository() {
    if (myRepository != null) {
      return myRepository;
    }
    myRepository = myGitRepositoryManager.getRepositoryForRoot(myContentRoot);
    return myRepository;
  }

  protected void refreshApplicationRepository() {
    GitInit.refreshAndConfigureVcsMappings(getProject(), getRepositoryRoot(), getRepositoryRootFile().getAbsolutePath());
  }

  protected void pushApplication(@NotNull CloudGitApplication application) throws ServerRuntimeException {
    push(application, getRepository(), getRemoteName());
  }

  protected void push(@NotNull CloudGitApplication application, @NotNull GitRepository repository, @NotNull String remote)
    throws ServerRuntimeException {
    GitCommandResult gitPushResult
      = getGit().push(repository, remote, application.getGitUrl(), "master:master", false, createGitLineHandlerListener());
    checkGitResult(gitPushResult);
  }

  @NotNull
  protected GitRepository getRepository() throws ServerRuntimeException {
    GitRepository repository = findRepository();
    if (repository == null) {
      throw new ServerRuntimeException("Unable to find GIT repository for module root: " + myContentRoot);
    }
    return repository;
  }

  protected void fetch() throws ServerRuntimeException {
    final VirtualFile contentRoot = getRepositoryRoot();
    GitRepository repository = getRepository();
    final GitLineHandler fetchHandler = new GitLineHandler(getProject(), contentRoot, GitCommand.FETCH);
    fetchHandler.setUrl(getApplication().getGitUrl());
    fetchHandler.setSilent(false);
    fetchHandler.addParameters(getRemoteName());
    fetchHandler.addLineListener(createGitLineHandlerListener());
    performRemoteGitTask(fetchHandler, CloudBundle.getText("fetching.application", getCloudName()));

    repository.update();
  }

  protected void add() throws ServerRuntimeException {
    try {
      GitFileUtils.addFiles(getProject(), myContentRoot, myContentRoot);
    }
    catch (VcsException e) {
      throw new ServerRuntimeException(e);
    }
  }

  protected void commit() throws ServerRuntimeException {
    commit(COMMIT_MESSAGE);
  }

  protected void commit(String message) throws ServerRuntimeException {
    try {
      if (GitUtil.hasLocalChanges(true, getProject(), myContentRoot)) {
        GitLineHandler handler = new GitLineHandler(getProject(), myContentRoot, GitCommand.COMMIT);
        handler.setSilent(false);
        handler.setStdoutSuppressed(false);
        handler.addParameters("-m", message);
        handler.endOptions();
        Git.getInstance().runCommand(handler).getOutputOrThrow();
      }
    }
    catch (VcsException e) {
      throw new ServerRuntimeException(e);
    }
  }

  protected void performRemoteGitTask(final GitLineHandler handler, String title) throws ServerRuntimeException {
    ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
    if (indicator != null) {
      indicator.setText(title);
      handler.addLineListener(GitStandardProgressAnalyzer.createListener(indicator));
    }
    GitCommandResult result = myGit.runCommand(handler);
    if (!result.success()) {
      getLoggingHandler().println(result.getErrorOutputAsJoinedString());
      throw new ServerRuntimeException(result.getErrorOutputAsJoinedString());
    }
  }

  protected void refreshContentRoot() {
    ApplicationManager.getApplication().invokeLater(() -> getRepositoryRoot().refresh(false, true));
  }

  public void fetchAndRefresh() throws ServerRuntimeException {
    fetch();
    refreshContentRoot();
  }

  private String getRemoteName() {
    if (myRemoteName == null) {
      myRemoteName = myDefaultRemoteName;
    }
    return myRemoteName;
  }

  private String getCloudName() {
    return myCloudName;
  }

  protected CloudGitApplication findApplication() throws ServerRuntimeException {
    return getAgentTaskExecutor().execute(() -> getDeployment().findApplication());
  }

  protected CloudGitApplication getApplication() throws ServerRuntimeException {
    CloudGitApplication application = findApplication();
    if (application == null) {
      throw new ServerRuntimeException("Can't find the application: " + getApplicationName());
    }
    return application;
  }

  protected CloudGitApplication createApplication() throws ServerRuntimeException {
    return getAgentTaskExecutor().execute(() -> getDeployment().createApplication());
  }

  public CloudGitApplication findApplication4Repository() throws ServerRuntimeException {
    final List<String> repositoryUrls = new ArrayList<>();
    for (GitRemote remote : getRepository().getRemotes()) {
      for (String url : remote.getUrls()) {
        repositoryUrls.add(url);
      }
    }

    return getAgentTaskExecutor().execute(() -> getDeployment().findApplication4Repository(ArrayUtil.toStringArray(repositoryUrls)));
  }

  public class CloneJob {

    public File cloneToTemp(String gitUrl) throws ServerRuntimeException {
      File cloneDir;
      try {
        cloneDir = FileUtil.createTempDirectory("cloud", "clone");
      }
      catch (IOException e) {
        throw new ServerRuntimeException(e);
      }

      File cloneDirParent = cloneDir.getParentFile();
      String cloneDirName = cloneDir.getName();

      doClone(cloneDirParent, cloneDirName, gitUrl);
      return cloneDir;
    }

    public void cloneToModule(String gitUrl) throws ServerRuntimeException {
      File cloneDir = cloneToTemp(gitUrl);

      try {
        FileUtil.copyDir(cloneDir, getRepositoryRootFile());
      }
      catch (IOException e) {
        throw new ServerRuntimeException(e);
      }

      refreshApplicationRepository();
    }

    public void doClone(File cloneDirParent, String cloneDirName, String gitUrl) throws ServerRuntimeException {
      GitCommandResult gitCloneResult
        = getGit().clone(getProject(), cloneDirParent, gitUrl, cloneDirName, createGitLineHandlerListener());
      checkGitResult(gitCloneResult);
    }
  }

  public class CloneJobWithRemote extends CloneJob {

    public void doClone(File cloneDirParent, String cloneDirName, String gitUrl) throws ServerRuntimeException {
      final GitLineHandler handler = new GitLineHandler(getProject(), cloneDirParent, GitCommand.CLONE);
      handler.setSilent(false);
      handler.setStdoutSuppressed(false);
      handler.setUrl(gitUrl);
      handler.addParameters("--progress");
      handler.addParameters(gitUrl);
      handler.addParameters(cloneDirName);
      handler.addParameters("-o");
      handler.addParameters(getRemoteName());
      handler.addLineListener(createGitLineHandlerListener());
      performRemoteGitTask(handler, CloudBundle.getText("cloning.existing.application", getCloudName()));
    }
  }
}
