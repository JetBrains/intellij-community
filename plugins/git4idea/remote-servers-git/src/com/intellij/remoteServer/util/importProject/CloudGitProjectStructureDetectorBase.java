/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.ide.util.importProject.ProjectDescriptor;
import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.ide.util.projectWizard.importSources.DetectedProjectRoot;
import com.intellij.ide.util.projectWizard.importSources.JavaModuleSourceRoot;
import com.intellij.ide.util.projectWizard.importSources.ProjectFromSourcesBuilder;
import com.intellij.ide.util.projectWizard.importSources.ProjectStructureDetector;
import com.intellij.ide.util.projectWizard.importSources.impl.JavaProjectStructureDetector;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.remoteServer.ServerType;
import com.intellij.remoteServer.util.CloudAccountSelectionEditor;
import com.intellij.remoteServer.util.CloudDeploymentNameConfiguration;
import com.intellij.remoteServer.util.CloudGitDeploymentDetector;
import git4idea.GitPlatformFacade;
import git4idea.GitUtil;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryImpl;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author michael.golubev
 */
public abstract class CloudGitProjectStructureDetectorBase<DC extends CloudDeploymentNameConfiguration> extends ProjectStructureDetector {

  private final JavaProjectStructureDetector myJavaDetector = new JavaProjectStructureDetector();

  private final String myId;
  private final String myJavaSourceRootTypeName;
  private final CloudGitDeploymentDetector myDeploymentDetector;

  protected CloudGitProjectStructureDetectorBase(ServerType serverType, CloudGitDeploymentDetector deploymentDetector) {
    myId = serverType.getPresentableName();
    myJavaSourceRootTypeName = "Java/" + myId;
    myDeploymentDetector = deploymentDetector;
  }

  @NotNull
  @Override
  public DirectoryProcessingResult detectRoots(@NotNull File dir,
                                               @NotNull File[] children,
                                               @NotNull File base,
                                               @NotNull List<DetectedProjectRoot> result) {
    detectApplicationRoot(dir, result);

    for (DetectedProjectRoot projectRoot : result) {
      if ((projectRoot instanceof CloudGitProjectRoot) && FileUtil.isAncestor(projectRoot.getDirectory(), dir, true)) {
        return detectJavaRoots(dir, children, base, result);
      }
    }

    return DirectoryProcessingResult.PROCESS_CHILDREN;
  }

  private void detectApplicationRoot(@NotNull File dir, @NotNull List<DetectedProjectRoot> result) {
    VirtualFile repositoryRoot = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(dir);
    if (repositoryRoot == null) {
      return;
    }

    final File gitDir = new File(dir, GitUtil.DOT_GIT);
    if (!gitDir.exists()) {
      return;
    }

    Project project = ProjectManager.getInstance().getDefaultProject();
    GitRepository repository
      = GitRepositoryImpl.getLightInstance(repositoryRoot, project, ServiceManager.getService(project, GitPlatformFacade.class), project);
    repository.update();

    List<String> applicationNames = myDeploymentDetector.collectApplicationNames(repository);
    if (applicationNames.isEmpty()) {
      return;
    }

    result.add(new CloudGitProjectRoot(myId, myJavaSourceRootTypeName, dir, repositoryRoot, applicationNames.get(0)));
  }

  private DirectoryProcessingResult detectJavaRoots(@NotNull File dir,
                                                    @NotNull File[] children,
                                                    @NotNull File base,
                                                    @NotNull List<DetectedProjectRoot> result) {
    List<DetectedProjectRoot> detectedJavaRoots = new ArrayList<DetectedProjectRoot>();
    DirectoryProcessingResult processingResult = myJavaDetector.detectRoots(dir, children, base, detectedJavaRoots);
    for (DetectedProjectRoot detectedJavaRoot : detectedJavaRoots) {
      if (detectedJavaRoot instanceof JavaModuleSourceRoot) {
        result.add(new CloudGitJavaSourceRoot(myJavaSourceRootTypeName, (JavaModuleSourceRoot)detectedJavaRoot));
      }
    }
    return processingResult;
  }

  @Override
  public String getDetectorId() {
    return myId;
  }

  @Override
  public List<ModuleWizardStep> createWizardSteps(ProjectFromSourcesBuilder builder,
                                                  ProjectDescriptor projectDescriptor,
                                                  Icon stepIcon) {
    return Collections.<ModuleWizardStep>singletonList(new CloudGitChooseAccountStep<DC>(this, builder, projectDescriptor));
  }

  public abstract CloudAccountSelectionEditor<?, DC, ?> createAccountSelectionEditor();

  public abstract DC createDeploymentConfiguration();
}
