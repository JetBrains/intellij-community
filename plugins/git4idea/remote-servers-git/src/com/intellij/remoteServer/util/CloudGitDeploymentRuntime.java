package com.intellij.remoteServer.util;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.remoteServer.agent.util.CloudGitAgent;
import com.intellij.remoteServer.agent.util.CloudGitAgentDeployment;
import com.intellij.remoteServer.agent.util.CloudGitApplication;
import com.intellij.remoteServer.agent.util.CloudLoggingHandler;
import com.intellij.remoteServer.configuration.deployment.DeploymentSource;
import com.intellij.remoteServer.configuration.deployment.ModuleDeploymentSource;
import com.intellij.remoteServer.runtime.ServerTaskExecutor;
import com.intellij.remoteServer.runtime.deployment.DeploymentLogManager;
import com.intellij.remoteServer.runtime.deployment.DeploymentRuntime;
import com.intellij.remoteServer.runtime.deployment.DeploymentTask;
import com.intellij.remoteServer.runtime.deployment.ServerRuntimeInstance;
import com.intellij.remoteServer.runtime.log.LoggingHandler;
import com.intellij.util.ThrowableRunnable;
import com.intellij.util.concurrency.Semaphore;
import git4idea.GitUtil;
import git4idea.actions.GitInit;
import git4idea.commands.*;
import git4idea.repo.GitRemote;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;

/**
 * @author michael.golubev
 */
public abstract class CloudGitDeploymentRuntime<DC extends CloudDeploymentNameConfiguration,
  AD extends CloudGitAgentDeployment,
  A extends CloudGitAgent<?, AD>> extends DeploymentRuntime {

  private static final Logger LOG = Logger.getInstance("#" + CloudGitDeploymentRuntime.class.getName());
  private final String myApplicationName;
  private final CloudConfigurationBase myConfiguration;

  private final Project myProject;
  private final GitRepositoryManager myGitRepositoryManager;
  private final Git myGit;

  private final VirtualFile myContentRoot;
  private final File myContentRootFile;
  private final AgentTaskExecutor myAgentTaskExecutor;
  private final String myPresentableName;
  private final CloudLoggingHandler myLoggingHandler;
  private final ServerTaskExecutor myTasksExecutor;
  private final AD myDeployment;

  private final DeploymentLogManager myLogManager;
  private final String myRemoteName;
  private final String myCloudName;

  private GitRepository myRepository;

  public CloudGitDeploymentRuntime(CloudConfigurationBase serverConfiguration,
                                   A agent,
                                   ServerTaskExecutor taskExecutor,
                                   DeploymentTask<DC> task,
                                   AgentTaskExecutor agentTaskExecutor,
                                   @Nullable DeploymentLogManager logManager,
                                   CloudDeploymentNameProvider deploymentNameProvider,
                                   String remoteName,
                                   String cloudName) throws ServerRuntimeException {
    myConfiguration = serverConfiguration;
    myTasksExecutor = taskExecutor;
    myLogManager = logManager;

    myRemoteName = remoteName;
    myCloudName = cloudName;

    DeploymentSource deploymentSource = task.getSource();
    if (!(deploymentSource instanceof ModuleDeploymentSource)) {
      throw new ServerRuntimeException("Module deployment source is the only supported");
    }

    ModuleDeploymentSource moduleDeploymentSource = (ModuleDeploymentSource)deploymentSource;
    Module module = moduleDeploymentSource.getModule();
    if (module == null) {
      throw new ServerRuntimeException("Module not found: " + moduleDeploymentSource.getModulePointer().getModuleName());
    }

    VirtualFile contentRoot = moduleDeploymentSource.getContentRoot();
    LOG.assertTrue(contentRoot != null, "Content root is not found");
    myContentRoot = contentRoot;

    File contentRootFile = moduleDeploymentSource.getFile();
    LOG.assertTrue(contentRootFile != null, "Content root file is not found");
    myContentRootFile = contentRootFile;

    myProject = task.getProject();
    myGitRepositoryManager = GitUtil.getRepositoryManager(myProject);
    myGit = ServiceManager.getService(Git.class);
    if (myGit == null) {
      throw new ServerRuntimeException("Can't initialize GIT");
    }

    myAgentTaskExecutor = agentTaskExecutor;
    myLoggingHandler = logManager == null ? new CloudSilentLoggingHandlerImpl() : new CloudLoggingHandlerImpl(logManager);

    myPresentableName = deploymentSource.getPresentableName();

    DC deploymentConfiguration = task.getConfiguration();
    myApplicationName = deploymentConfiguration.isDefaultDeploymentName()
                        ? deploymentNameProvider.getDeploymentName(deploymentSource)
                        : deploymentConfiguration.getDeploymentName();

    myDeployment = agent.createDeployment(getApplicationName(), myLoggingHandler);
  }

  public AgentTaskExecutor getAgentTaskExecutor() {
    return myAgentTaskExecutor;
  }

  public AD getDeployment() {
    return myDeployment;
  }

  public void deploy(ServerRuntimeInstance.DeploymentOperationCallback callback) {
    try {
      deploy();
      callback.succeeded(this);
    }
    catch (ServerRuntimeException e) {
      callback.errorOccurred(e.getMessage());
    }
  }

  public void deploy() throws ServerRuntimeException {
    VirtualFile contentRoot = getContentRoot();

    CloudGitApplication application = findApplication();
    if (application == null) {
      application = createApplication();
    }

    GitRepository repository = findRepository();
    if (repository == null) {
      myLoggingHandler.println("Initializing git repository...");
      GitCommandResult gitInitResult = getGit().init(getProject(), contentRoot, createGitLineHandlerListener());
      checkGitResult(gitInitResult);

      refreshApplicationRepository();

      repository = getRepository();
    }

    GitRemote gitRemote = GitUtil.findRemoteByName(repository, getRemoteName());
    if (gitRemote == null) {
      addGitRemote(application);
    }
    else if (!gitRemote.getUrls().contains(application.getGitUrl())) {
      resetGitRemote(application);
    }

    try {
      GitSimpleHandler handler = new GitSimpleHandler(getProject(), contentRoot, GitCommand.ADD);
      handler.setSilent(false);
      handler.addParameters(".");
      handler.run();
    }
    catch (VcsException e) {
      throw new ServerRuntimeException(e);
    }

    try {
      if (GitUtil.hasLocalChanges(true, getProject(), contentRoot)) {
        GitSimpleHandler handler = new GitSimpleHandler(getProject(), contentRoot, GitCommand.COMMIT);
        handler.setSilent(false);
        handler.addParameters("-a");
        handler.addParameters("-m", "Deploy");
        handler.endOptions();
        handler.run();
      }
    }
    catch (VcsException e) {
      throw new ServerRuntimeException(e);
    }

    repository.update();

    pushApplication(getRemoteName(), application.getGitUrl());

    if (myLogManager != null) {
      LoggingHandler loggingHandler = myLogManager.getMainLoggingHandler();
      loggingHandler.print("Application is available at ");
      loggingHandler.printHyperlink(application.getWebUrl());
      loggingHandler.print("\n");
    }
  }

  @Override
  public void undeploy(final @NotNull UndeploymentTaskCallback callback) {
    myTasksExecutor.submit(new ThrowableRunnable<Exception>() {

      @Override
      public void run() throws Exception {
        try {
          undeploy();
          callback.succeeded();
        }
        catch (ServerRuntimeException e) {
          callback.errorOccurred(e.getMessage());
        }
      }
    }, callback);
  }

  public void undeploy() throws ServerRuntimeException {
    if (!confirmUndeploy()) {
      throw new ServerRuntimeException("Undeploy cancelled");
    }
    myAgentTaskExecutor.execute(new Computable<Object>() {

      @Override
      public Object compute() {
        myDeployment.deleteApplication();
        return null;
      }
    });
  }

  public boolean isDeployed() throws ServerRuntimeException {
    return findApplication() != null;
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

  protected Project getProject() {
    return myProject;
  }

  private String getApplicationName() {
    return myApplicationName;
  }

  private Git getGit() {
    return myGit;
  }

  protected VirtualFile getContentRoot() {
    return myContentRoot;
  }

  private File getContentRootFile() {
    return myContentRootFile;
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
        myLoggingHandler.println(line);
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
      final GitSimpleHandler handler = new GitSimpleHandler(myProject, myContentRoot, GitCommand.REMOTE);
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
    GitInit.refreshAndConfigureVcsMappings(myProject, getContentRoot(), getContentRootFile().getAbsolutePath());
  }

  protected void pushApplication(String remoteName, String gitUrl) throws ServerRuntimeException {
    GitCommandResult gitPushResult
      = getGit().push(getRepository(), remoteName, gitUrl, "master:master", createGitLineHandlerListener());
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
    final VirtualFile contentRoot = getContentRoot();
    GitRepository repository = getRepository();
    final GitLineHandler fetchHandler = new GitLineHandler(getProject(), contentRoot, GitCommand.FETCH);
    fetchHandler.setSilent(false);
    fetchHandler.addParameters(getRemoteName());
    fetchHandler.addLineListener(createGitLineHandlerListener());
    performRemoteGitTask(fetchHandler, CloudBundle.getText("fetching.application", getCloudName()));

    repository.update();
  }

  protected void performRemoteGitTask(final GitLineHandler handler, String title) throws ServerRuntimeException {
    final GitTask task = new GitTask(myProject, handler, title);
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
              myLoggingHandler.println(error.toString());
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
        getContentRoot().refresh(false, true);
      }
    });
  }

  protected boolean confirmUndeploy() {
    final Ref<Boolean> confirmed = new Ref<Boolean>(false);
    ApplicationManager.getApplication().invokeAndWait(new Runnable() {

      @Override
      public void run() {
        String title = CloudBundle.getText("cloud.undeploy.confirm.title");
        while (true) {
          String password = Messages.showPasswordDialog(CloudBundle.getText("cloud.undeploy.confirm.message", myPresentableName), title);
          if (password == null) {
            return;
          }
          if (password.equals(myConfiguration.getPassword())) {
            confirmed.set(true);
            return;
          }
          Messages.showErrorDialog(CloudBundle.getText("cloud.undeploy.confirm.password.incorrect"), title);
        }
      }
    }, ModalityState.defaultModalityState());
    return confirmed.get();
  }

  private String getRemoteName() {
    return myRemoteName;
  }

  private String getCloudName() {
    return myCloudName;
  }

  protected CloudGitApplication findApplication() throws ServerRuntimeException {
    return myAgentTaskExecutor.execute(new Computable<CloudGitApplication>() {

      @Override
      public CloudGitApplication compute() {
        return myDeployment.findApplication();
      }
    });
  }

  protected CloudGitApplication createApplication() throws ServerRuntimeException {
    return myAgentTaskExecutor.execute(new Computable<CloudGitApplication>() {

      @Override
      public CloudGitApplication compute() {
        return myDeployment.createApplication();
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
        FileUtil.copyDir(cloneDir, getContentRootFile());
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
