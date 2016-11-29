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

import com.intellij.ProjectTopics;
import com.intellij.ide.util.importProject.ModuleDescriptor;
import com.intellij.ide.util.importProject.ProjectDescriptor;
import com.intellij.ide.util.projectWizard.ModuleBuilder;
import com.intellij.ide.util.projectWizard.importSources.DetectedProjectRoot;
import com.intellij.ide.util.projectWizard.importSources.DetectedSourceRoot;
import com.intellij.ide.util.projectWizard.importSources.ProjectFromSourcesBuilder;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.StdModuleTypes;
import com.intellij.openapi.project.ModuleAdapter;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.Ref;
import com.intellij.remoteServer.util.CloudGitDeploymentDetector;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.messages.MessageBusConnection;
import git4idea.actions.GitInit;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * @author michael.golubev
 */
public class CloudGitChooseAccountStepImpl extends CloudGitChooseAccountStepBase {

  private final ProjectFromSourcesBuilder myBuilder;
  private final ProjectDescriptor myProjectDescriptor;

  private final CloudGitProjectStructureDetector myStructureDetector;

  public CloudGitChooseAccountStepImpl(CloudGitDeploymentDetector deploymentDetector,
                                       CloudGitProjectStructureDetector structureDetector,
                                       ProjectFromSourcesBuilder builder,
                                       ProjectDescriptor projectDescriptor) {
    super(deploymentDetector, builder.getContext());
    myBuilder = builder;
    myProjectDescriptor = projectDescriptor;

    myStructureDetector = structureDetector;
  }

  @Override
  public boolean isStepVisible() {
    final Ref<Boolean> result = new Ref<>(false);
    new RootIterator() {

      @Override
      protected void processProjectRoot(CloudGitProjectRoot root) {
        result.set(true);
      }

      @Override
      protected void processJavaSourceRoot(DetectedSourceRoot root) {

      }
    }.iterate();
    return result.get();
  }

  @Override
  public void updateDataModel() {
    super.updateDataModel();
    final MultiMap<CloudGitProjectRoot, DetectedSourceRoot> project2sourceRoots = new MultiMap<>();
    new RootIterator() {

      CloudGitProjectRoot lastProjectRoot = null;

      @Override
      protected void processProjectRoot(CloudGitProjectRoot root) {
        lastProjectRoot = root;
        project2sourceRoots.put(lastProjectRoot, new ArrayList<>());
      }

      @Override
      protected void processJavaSourceRoot(DetectedSourceRoot root) {
        project2sourceRoots.putValue(lastProjectRoot, root);
      }
    }.iterate();

    List<ModuleDescriptor> modules = new ArrayList<>(myProjectDescriptor.getModules());
    for (Map.Entry<CloudGitProjectRoot, Collection<DetectedSourceRoot>> project2sourceRootsEntry : project2sourceRoots.entrySet()) {
      final CloudGitProjectRoot projectRoot = project2sourceRootsEntry.getKey();
      final File directory = projectRoot.getDirectory();
      ModuleDescriptor moduleDescriptor = new ModuleDescriptor(directory, StdModuleTypes.JAVA, project2sourceRootsEntry.getValue());
      final String applicationName = projectRoot.getApplicationName();
      moduleDescriptor.addConfigurationUpdater(new ModuleBuilder.ModuleConfigurationUpdater() {

        @Override
        public void update(final @NotNull Module module, @NotNull ModifiableRootModel rootModel) {
          final MessageBusConnection connection = module.getProject().getMessageBus().connect();
          connection.subscribe(ProjectTopics.MODULES, new ModuleAdapter() {

            @Override
            public void moduleAdded(@NotNull Project project, @NotNull Module addedModule) {
              if (addedModule == module) {
                StartupManager.getInstance(project).runWhenProjectIsInitialized(() -> onModuleAdded(module));
                connection.disconnect();
              }
            }
          });
        }

        private void onModuleAdded(Module module) {
          createRunConfiguration(module, applicationName);
          GitInit.refreshAndConfigureVcsMappings(module.getProject(), projectRoot.getRepositoryRoot(), directory.getAbsolutePath());
        }
      });
      modules.add(moduleDescriptor);
    }
    myProjectDescriptor.setModules(modules);
  }

  private abstract class RootIterator {

    public void iterate() {
      Collection<DetectedProjectRoot> roots = myBuilder.getProjectRoots(myStructureDetector);

      CloudGitDeploymentDetector point = getDeploymentDetector();
      String projectRootTypeName = CloudGitProjectRoot.getProjectRootTypeName(point);
      String javaSourceRootTypeName = CloudGitProjectRoot.getJavaSourceRootTypeName(point);

      for (DetectedProjectRoot root : roots) {
        if ((root instanceof CloudGitProjectRoot) && root.getRootTypeName().equals(projectRootTypeName)) {
          processProjectRoot((CloudGitProjectRoot)root);
        }
        else if ((root instanceof DetectedSourceRoot) && root.getRootTypeName().equals(javaSourceRootTypeName)) {
          processJavaSourceRoot((DetectedSourceRoot)root);
        }
      }
    }

    protected abstract void processProjectRoot(CloudGitProjectRoot root);

    protected abstract void processJavaSourceRoot(DetectedSourceRoot root);
  }
}
