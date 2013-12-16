package com.intellij.remoteServer.util;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.remoteServer.agent.util.CloudGitApplication;
import com.intellij.remoteServer.configuration.deployment.DeploymentSource;
import com.intellij.remoteServer.runtime.deployment.DeploymentLogManager;
import com.intellij.remoteServer.runtime.deployment.DeploymentTask;
import com.intellij.util.concurrency.Semaphore;
import git4idea.GitUtil;
import git4idea.actions.GitInit;
import git4idea.commands.*;
import git4idea.repo.GitRemote;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import git4idea.util.GitFileUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;

/**
 * @author michael.golubev
 */
public class CloudGitDeploymentRuntime extends CloudDeploymentRuntime {

  private static final Logger LOG = Logger.getInstance("#" + CloudGitDeploymentRuntime.class.getName());

  private final GitRepositoryManager myGitRepositoryManager;
  private final Git myGit;

  private final VirtualFile myContentRoot;
  private final File myRepositoryRootFile;

  private final String myDefaultRemoteName;
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

    myGitRepositoryManager = GitUtil.getRepositoryManager(getProject());
    myGit = ServiceManager.getService(Git.class);
    if (myGit == null) {
      throw new ServerRuntimeException("Can't initialize GIT");
    }
  }

  @Override
  public CloudGitApplication deploy() throws ServerRuntimeException {
    CloudGitApplication application = findOrCreateApplication();
    GitRepository repository = findOrCreateRepository();
    addOrResetGitRemote(application, repository);
    add();
    commit();
    repository.update();
    pushApplication(application);
    return application;
  }

  public void undeploy() throws ServerRuntimeException {
    getAgentTaskExecutor().execute(new Computable<Object>() {

      @Override
      public Object compute() {
        getDeployment().deleteApplication();
        return null;
      }
    });
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
    CloudGitApplication application = findApplication();
    if (application == null) {
      throw new ServerRuntimeException("Can't find the application: " + getApplicationName());
    }

    new CloneJobWithRemote().cloneToModule(application.getGitUrl());

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
      Throwable exception = commandResult.getException();
      if (exception != null) {
        LOG.info(exception);
        throw new ServerRuntimeException(exception);
      }
      else {
        throw new ServerRuntimeException(commandResult.getErrorOutputAsJoinedString());
      }
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
      final GitSimpleHandler handler = new GitSimpleHandler(getProject(), myContentRoot, GitCommand.REMOTE);
      handler.setSilent(false);
      handler.addParameters(subCommand, remoteName, application.getGitUrl());
      handler.run();
      getRepository().update();
      if (handler.getExitCode() != 0) {
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
    GitCommandResult gitPushResult
      = getGit().push(getRepository(), getRemoteName(), application.getGitUrl(), "master:master", createGitLineHandlerListener());
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
    try {
      if (GitUtil.hasLocalChanges(true, getProject(), myContentRoot)) {
        GitSimpleHandler handler = new GitSimpleHandler(getProject(), myContentRoot, GitCommand.COMMIT);
        handler.setSilent(false);
        handler.addParameters("-m", "Deploy");
        handler.endOptions();
        handler.run();
      }
    }
    catch (VcsException e) {
      throw new ServerRuntimeException(e);
    }
  }

  protected void performRemoteGitTask(final GitLineHandler handler, String title) throws ServerRuntimeException {
    final GitTask task = new GitTask(getProject(), handler, title);
    task.setProgressAnalyzer(new GitStandardProgressAnalyzer());

    final Semaphore semaphore = new Semaphore();
    semaphore.down();

    final Ref<ServerRuntimeException> errorRef = new Ref<ServerRuntimeException>();

    ApplicationManager.getApplication().invokeLater(new Runnable() {

      @Override
      public void run() {
        task.execute(false, false, new GitTaskResultHandlerAdapter() {

          @Override
          protected void run(GitTaskResult result) {
            super.run(result);
            semaphore.up();
          }

          @Override
          protected void onFailure() {
            for (VcsException error : handler.errors()) {
              getLoggingHandler().println(error.toString());
              if (errorRef.isNull()) {
                errorRef.set(new ServerRuntimeException(error));
              }
            }
          }
        });
      }
    });

    semaphore.waitFor();
    if (!errorRef.isNull()) {
      throw errorRef.get();
    }
  }

  protected void refreshContentRoot() {
    ApplicationManager.getApplication().invokeLater(new Runnable() {

      @Override
      public void run() {
        getRepositoryRoot().refresh(false, true);
      }
    });
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
    return getAgentTaskExecutor().execute(new Computable<CloudGitApplication>() {

      @Override
      public CloudGitApplication compute() {
        return getDeployment().findApplication();
      }
    });
  }

  protected CloudGitApplication createApplication() throws ServerRuntimeException {
    return getAgentTaskExecutor().execute(new Computable<CloudGitApplication>() {

      @Override
      public CloudGitApplication compute() {
        return getDeployment().createApplication();
      }
    });
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
