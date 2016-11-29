/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.remoteServer.util.importProject;

import com.intellij.execution.RunManager;
import com.intellij.execution.RunManagerAdapter;
import com.intellij.execution.RunManagerEx;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.ide.actions.ImportModuleAction;
import com.intellij.ide.util.importProject.RootsDetectionStep;
import com.intellij.ide.util.newProjectWizard.AbstractProjectWizard;
import com.intellij.ide.util.newProjectWizard.AddModuleWizard;
import com.intellij.ide.util.newProjectWizard.StepSequence;
import com.intellij.ide.util.projectWizard.ImportFromSourcesProvider;
import com.intellij.ide.wizard.Step;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationListener;
import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.remoteServer.ServerType;
import com.intellij.remoteServer.configuration.deployment.DeploymentSource;
import com.intellij.remoteServer.configuration.deployment.ModuleDeploymentSource;
import com.intellij.remoteServer.impl.configuration.deployment.DeployToServerConfigurationTypesRegistrar;
import com.intellij.remoteServer.impl.configuration.deployment.DeployToServerRunConfiguration;
import com.intellij.remoteServer.impl.configuration.deployment.ModuleDeploymentSourceImpl;
import com.intellij.remoteServer.util.CloudBundle;
import com.intellij.remoteServer.util.CloudGitDeploymentDetector;
import com.intellij.remoteServer.util.CloudNotifier;
import com.intellij.util.Function;
import com.intellij.util.containers.hash.HashMap;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryChangeListener;
import git4idea.repo.GitRepositoryManager;
import org.jetbrains.annotations.NotNull;

import javax.swing.event.HyperlinkEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author michael.golubev
 */
public class CloudGitRemoteDetector extends AbstractProjectComponent implements GitRepositoryChangeListener {

  private final GitRepositoryManager myRepositoryManager;
  private final RunManagerEx myRunManager;

  private final CloudNotifier myNotifier;

  private final List<CloudTypeDelegate> myDelegates;

  public CloudGitRemoteDetector(Project project, GitRepositoryManager repositoryManager, RunManager runManager) {
    super(project);
    myRepositoryManager = repositoryManager;
    myRunManager = (RunManagerEx)runManager;

    myNotifier = new CloudNotifier("Git remotes detector");

    myDelegates = new ArrayList<>();
    for (CloudGitDeploymentDetector deploymentDetector : CloudGitDeploymentDetector.EP_NAME.getExtensions()) {
      myDelegates.add(new CloudTypeDelegate(deploymentDetector));
    }
  }

  @Override
  public void projectOpened() {
    myProject.getMessageBus().connect().subscribe(GitRepository.GIT_REPO_CHANGE, this);

    myRunManager.addRunManagerListener(new RunManagerAdapter() {

      @Override
      public void runConfigurationAdded(@NotNull RunnerAndConfigurationSettings settings) {
        onRunConfigurationAddedOrChanged(settings);
      }

      @Override
      public void runConfigurationChanged(@NotNull RunnerAndConfigurationSettings settings) {
        onRunConfigurationAddedOrChanged(settings);
      }
    });
  }

  private void onRunConfigurationAddedOrChanged(RunnerAndConfigurationSettings settings) {
    RunConfiguration runConfiguration = settings.getConfiguration();
    for (CloudTypeDelegate delegate : myDelegates) {
      delegate.onRunConfigurationAddedOrChanged(runConfiguration);
    }
  }

  @Override
  public void repositoryChanged(@NotNull GitRepository repository) {
    for (CloudTypeDelegate delegate : myDelegates) {
      delegate.repositoryChanged(repository);
    }
  }

  private class CloudTypeDelegate {

    private final CloudGitDeploymentDetector myDeploymentDetector;

    private Map<GitRepository, RepositoryNotifier> myRepositoryToNotifier = new HashMap<>();

    public CloudTypeDelegate(CloudGitDeploymentDetector deploymentDetector) {
      myDeploymentDetector = deploymentDetector;
    }

    private ServerType getCloudType() {
      return myDeploymentDetector.getCloudType();
    }

    public void repositoryChanged(@NotNull GitRepository repository) {
      String applicationName = myDeploymentDetector.getFirstApplicationName(repository);
      if (applicationName == null) {
        forget(repository);
      }
      else {
        RepositoryNotifier notifier = myRepositoryToNotifier.get(repository);
        if (notifier == null && !hasRunConfig4Repository(repository)) {
          notifier = new RepositoryNotifier(myDeploymentDetector, repository);
          myRepositoryToNotifier.put(repository, notifier);
        }
        if (notifier != null) {
          notifier.setApplicationName(applicationName);
        }
      }
    }

    private boolean hasRunConfig4Repository(GitRepository repository) {
      List<RunConfiguration> runConfigurations
        = myRunManager.getConfigurationsList(DeployToServerConfigurationTypesRegistrar.getDeployConfigurationType(getCloudType()));

      VirtualFile repositoryRoot = repository.getRoot();

      for (RunConfiguration runConfiguration : runConfigurations) {
        if (repositoryRoot.equals(getContentRoot(runConfiguration))) {
          return true;
        }
      }
      return false;
    }

    private VirtualFile getContentRoot(RunConfiguration runConfiguration) {
      if (!(runConfiguration instanceof DeployToServerRunConfiguration)) {
        return null;
      }

      DeployToServerRunConfiguration deployRunConfiguration = (DeployToServerRunConfiguration)runConfiguration;
      if (deployRunConfiguration.getServerType() != getCloudType()) {
        return null;
      }

      DeploymentSource deploymentSource = deployRunConfiguration.getDeploymentSource();
      if (!(deploymentSource instanceof ModuleDeploymentSource)) {
        return null;
      }

      return ((ModuleDeploymentSource)deploymentSource).getContentRoot();
    }

    public void onRunConfigurationAddedOrChanged(RunConfiguration runConfiguration) {
      VirtualFile contentRoot = getContentRoot(runConfiguration);
      if (contentRoot == null) {
        return;
      }

      GitRepository repository = myRepositoryManager.getRepositoryForRoot(contentRoot);
      if (repository == null) {
        return;
      }

      forget(repository);
    }

    private void forget(GitRepository repository) {
      RepositoryNotifier notifier = myRepositoryToNotifier.remove(repository);
      if (notifier != null) {
        notifier.expire();
      }
    }
  }

  private class RepositoryNotifier {

    private final CloudGitDeploymentDetector myDeploymentDetector;

    private final VirtualFile myRepositoryRoot;
    private final Notification myNotification;

    private final String myCloudName;

    private String myApplicationName;

    public RepositoryNotifier(CloudGitDeploymentDetector deploymentDetector, GitRepository repository) {
      myDeploymentDetector = deploymentDetector;
      myRepositoryRoot = repository.getRoot();
      myCloudName = deploymentDetector.getCloudType().getPresentableName();
      String path = FileUtil.toSystemDependentName(myRepositoryRoot.getPath());
      myNotification = myNotifier.showMessage(CloudBundle.getText("git.cloud.app.detected", myCloudName, path),
                                              MessageType.INFO,
                                              new NotificationListener() {

                                                @Override
                                                public void hyperlinkUpdate(@NotNull Notification notification,
                                                                            @NotNull HyperlinkEvent event) {
                                                  setupRunConfiguration();
                                                }
                                              });
    }

    public void setApplicationName(String applicationName) {
      myApplicationName = applicationName;
    }

    public void expire() {
      myNotification.expire();
    }

    public void setupRunConfiguration() {
      Module targetModule = null;
      for (Module module : ModuleManager.getInstance(myProject).getModules()) {
        if (myRepositoryRoot.equals(ModuleDeploymentSourceImpl.getContentRoot(module))) {
          targetModule = module;
          break;
        }
      }

      if (targetModule == null) {
        AddModuleWizard wizard = new AddModuleWizard(myProject, myRepositoryRoot.getPath(), new ImportFromSourcesProvider());
        wizard.navigateToStep(step -> step instanceof RootsDetectionStep);
        if (wizard.getStepCount() > 0 && !wizard.showAndGet()) {
          return;
        }
        ImportModuleAction.createFromWizard(myProject, wizard);
      }
      else {
        final Ref<CloudGitChooseAccountStepBase> chooseAccountStepRef = new Ref<>();
        if (!new AbstractProjectWizard(CloudBundle.getText("choose.account.wizzard.title", myCloudName), myProject, (String)null) {

          final StepSequence myStepSequence;

          {
            CloudGitChooseAccountStepBase chooseAccountStep = new CloudGitChooseAccountStepBase(myDeploymentDetector, myWizardContext);
            chooseAccountStepRef.set(chooseAccountStep);
            myStepSequence = new StepSequence(chooseAccountStep);
            addStep(chooseAccountStep);
            init();
          }

          @Override
          public StepSequence getSequence() {
            return myStepSequence;
          }
        }.showAndGet()) {
          return;
        }
        chooseAccountStepRef.get().createRunConfiguration(targetModule, myApplicationName);
      }
    }
  }
}
