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
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.remoteServer.util.CloudGitDeploymentDetector;
import git4idea.GitUtil;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryImpl;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * @author michael.golubev
 */
public class CloudGitProjectStructureDetector extends ProjectStructureDetector {

  private final JavaProjectStructureDetector myJavaDetector = new JavaProjectStructureDetector();

  @NotNull
  @Override
  public DirectoryProcessingResult detectRoots(@NotNull File dir,
                                               @NotNull File[] children,
                                               @NotNull File base,
                                               @NotNull List<DetectedProjectRoot> result) {
    detectApplicationRoot(dir, result);

    for (DetectedProjectRoot projectRoot : result) {
      if ((projectRoot instanceof CloudGitProjectRoot) && FileUtil.isAncestor(projectRoot.getDirectory(), dir, true)) {
        return detectJavaRoots(((CloudGitProjectRoot)projectRoot).getJavaSourceRootTypeName(), dir, children, base, result);
      }
    }

    return DirectoryProcessingResult.PROCESS_CHILDREN;
  }

  private static void detectApplicationRoot(@NotNull File dir, @NotNull List<DetectedProjectRoot> result) {
    VirtualFile repositoryRoot = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(dir);
    if (repositoryRoot == null) {
      return;
    }
    if (GitUtil.findGitDir(repositoryRoot) == null) {
      return;
    }

    Project project = ProjectManager.getInstance().getDefaultProject();
    GitRepository repository = GitRepositoryImpl.getInstance(repositoryRoot, project, false);

    for (CloudGitDeploymentDetector deploymentDetector : CloudGitDeploymentDetector.EP_NAME.getExtensions()) {
      String applicationName = deploymentDetector.getFirstApplicationName(repository);
      if (applicationName != null) {
        result.add(new CloudGitProjectRoot(deploymentDetector, dir, repositoryRoot, applicationName));
      }
    }
  }

  private DirectoryProcessingResult detectJavaRoots(String javaSourceRootTypeName,
                                                    @NotNull File dir,
                                                    @NotNull File[] children,
                                                    @NotNull File base,
                                                    @NotNull List<DetectedProjectRoot> result) {
    List<DetectedProjectRoot> detectedJavaRoots = new ArrayList<>();
    DirectoryProcessingResult processingResult = myJavaDetector.detectRoots(dir, children, base, detectedJavaRoots);
    for (DetectedProjectRoot detectedJavaRoot : detectedJavaRoots) {
      if (detectedJavaRoot instanceof JavaModuleSourceRoot) {
        result.add(new CloudGitJavaSourceRoot(javaSourceRootTypeName, (JavaModuleSourceRoot)detectedJavaRoot));
      }
    }
    return processingResult;
  }

  @Override
  public List<ModuleWizardStep> createWizardSteps(ProjectFromSourcesBuilder builder,
                                                  ProjectDescriptor projectDescriptor,
                                                  Icon stepIcon) {
    List<ModuleWizardStep> result = new ArrayList<>();
    for (CloudGitDeploymentDetector deploymentDetector : CloudGitDeploymentDetector.EP_NAME.getExtensions()) {
      result.add(new CloudGitChooseAccountStepImpl(deploymentDetector, this, builder, projectDescriptor));
    }
    return result;
  }
}
