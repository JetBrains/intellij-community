/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package org.jetbrains.idea.devkit.module;

import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.ide.projectView.actions.MarkRootActionBase;
import com.intellij.ide.util.projectWizard.JavaModuleBuilder;
import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.ide.util.projectWizard.SettingsStep;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.module.*;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.SdkTypeId;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.DevKitBundle;
import org.jetbrains.idea.devkit.build.PluginBuildConfiguration;
import org.jetbrains.idea.devkit.projectRoots.IdeaJdk;
import org.jetbrains.idea.devkit.run.PluginConfigurationType;
import org.jetbrains.jps.model.java.JavaResourceRootType;

public class PluginModuleBuilder extends JavaModuleBuilder{


  public ModuleType getModuleType() {
    return PluginModuleType.getInstance();
  }

  public void setupRootModel(final ModifiableRootModel rootModel) throws ConfigurationException {
    super.setupRootModel(rootModel);
    String contentEntryPath = getContentEntryPath();
    if (contentEntryPath == null) return;

    String resourceRootPath = contentEntryPath + "/resources";
    VirtualFile contentRoot = LocalFileSystem.getInstance().findFileByPath(contentEntryPath);
    if (contentRoot == null) return;

    ContentEntry contentEntry = MarkRootActionBase.findContentEntry(rootModel, contentRoot);
    if (contentEntry != null) {
      contentEntry.addSourceFolder(VfsUtilCore.pathToUrl(resourceRootPath), JavaResourceRootType.RESOURCE);
    }

    final String defaultPluginXMLLocation = resourceRootPath + "/META-INF/plugin.xml";
    final Module module = rootModel.getModule();
    final Project project = module.getProject();
    StartupManager.getInstance(project).runWhenProjectIsInitialized(new Runnable() {
      public void run() {
        final PluginBuildConfiguration buildConfiguration = PluginBuildConfiguration.getInstance(module);
        if (buildConfiguration != null) {
          buildConfiguration.setPluginXmlPathAndCreateDescriptorIfDoesntExist(defaultPluginXMLLocation);
        }

        VirtualFile file = LocalFileSystem.getInstance().findFileByPath(defaultPluginXMLLocation);
        if (file != null) {
          FileEditorManager.getInstance(project).openFile(file, true);
        }
      }
    });
  }

  @Nullable
  @Override
  public Module commitModule(@NotNull Project project, @Nullable ModifiableModuleModel model) {
    Module module = super.commitModule(project, model);
    if (module != null) {
      RunManager runManager = RunManager.getInstance(project);
      RunnerAndConfigurationSettings configuration =
        runManager.createRunConfiguration(DevKitBundle.message("run.configuration.title"), new PluginConfigurationType().getConfigurationFactories()[0]);
      runManager.addConfiguration(configuration, false);
      runManager.setSelectedConfiguration(configuration);
    }
    return module;
  }

  @Override
  public boolean isSuitableSdkType(SdkTypeId sdk) {
    return sdk == IdeaJdk.getInstance();
  }

  @Override
  public String getParentGroup() {
    return JavaModuleType.JAVA_GROUP;
  }

  @Override
  public int getWeight() {
    return 0;
  }

  public ModuleWizardStep modifyProjectTypeStep(@NotNull SettingsStep settingsStep) {
    return StdModuleTypes.JAVA.modifyProjectTypeStep(settingsStep, this);
  }
}
