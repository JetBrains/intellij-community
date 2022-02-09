// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.module;

import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.ide.projectView.actions.MarkRootActionBase;
import com.intellij.ide.util.projectWizard.JavaModuleBuilder;
import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.ide.util.projectWizard.SettingsStep;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.module.Module;
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
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.SwingHelper;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.DevKitBundle;
import org.jetbrains.idea.devkit.build.PluginBuildConfiguration;
import org.jetbrains.idea.devkit.projectRoots.IdeaJdk;
import org.jetbrains.idea.devkit.run.PluginConfigurationType;
import org.jetbrains.jps.model.java.JavaResourceRootType;

import java.awt.*;

import static java.awt.GridBagConstraints.CENTER;
import static java.awt.GridBagConstraints.HORIZONTAL;

/**
 * @deprecated Completely replaced with @{link {@link DevKitModuleBuilder}.
 */
@Deprecated
@ApiStatus.ScheduledForRemoval(inVersion = "2022.1")
public class PluginModuleBuilder extends JavaModuleBuilder {

  @Override
  public ModuleType getModuleType() {
    return PluginModuleType.getInstance();
  }

  @Override
  public void setupRootModel(@NotNull final ModifiableRootModel rootModel) throws ConfigurationException {
    super.setupRootModel(rootModel);
    String contentEntryPath = getContentEntryPath();
    if (contentEntryPath == null) return;

    String resourceRootPath = contentEntryPath + "/resources"; //NON-NLS
    VirtualFile contentRoot = LocalFileSystem.getInstance().findFileByPath(contentEntryPath);
    if (contentRoot == null) return;

    ContentEntry contentEntry = MarkRootActionBase.findContentEntry(rootModel, contentRoot);
    if (contentEntry != null) {
      contentEntry.addSourceFolder(VfsUtilCore.pathToUrl(resourceRootPath), JavaResourceRootType.RESOURCE);
    }

    final String defaultPluginXMLLocation = resourceRootPath + "/" + PluginDescriptorConstants.PLUGIN_XML_PATH;
    final Module module = rootModel.getModule();
    final Project project = module.getProject();
    StartupManager.getInstance(project).runWhenProjectIsInitialized(() -> {
      final PluginBuildConfiguration buildConfiguration = PluginBuildConfiguration.getInstance(module);
      if (buildConfiguration != null) {
        buildConfiguration.setPluginXmlPathAndCreateDescriptorIfDoesntExist(defaultPluginXMLLocation);
      }

      VirtualFile file = LocalFileSystem.getInstance().findFileByPath(defaultPluginXMLLocation);
      if (file != null) {
        FileEditorManager.getInstance(project).openFile(file, true);
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
        runManager.createConfiguration(DevKitBundle.message("run.configuration.title"),
                                       new PluginConfigurationType().getConfigurationFactories()[0]);
      runManager.addConfiguration(configuration);
      runManager.setSelectedConfiguration(configuration);
    }
    return module;
  }

  @Override
  public boolean isAvailable() {
    return false;
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
    return IJ_PLUGIN_WEIGHT;
  }

  @Override
  public ModuleWizardStep modifyProjectTypeStep(@NotNull SettingsStep settingsStep) {
    final ModuleWizardStep step = StdModuleTypes.JAVA.modifyProjectTypeStep(settingsStep, this);
    if (step == null) return null;

    step.getComponent().add(SwingHelper.createHtmlLabel(DevKitBundle.message("module.wizard.devkit.simple.plugin.label"), null, null),
                            new GridBagConstraints(0, 1, 1, 1, 1.0, 1.0, CENTER, HORIZONTAL, JBUI.insetsTop(8), 0, 0));
    return step;
  }
}
