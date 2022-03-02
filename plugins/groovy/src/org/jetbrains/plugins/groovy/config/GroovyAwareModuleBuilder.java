// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.config;

import com.intellij.ide.util.projectWizard.JavaModuleBuilder;
import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.ide.util.projectWizard.SettingsStep;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import icons.JetgroovyIcons;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.config.wizard.GroovyProjectWizardUtils;

import javax.swing.*;

/**
 * @author peter
 */
public final class GroovyAwareModuleBuilder extends JavaModuleBuilder {

  private @Nullable String myPathToGroovySample = null;

  @Override
  public ModuleWizardStep modifyProjectTypeStep(@NotNull SettingsStep settingsStep) {
    return new GroovySdkForNewModuleWizardStep(this, settingsStep);
  }

  @Override
  public void setupRootModel(@NotNull ModifiableRootModel rootModel) throws ConfigurationException {
    super.setupRootModel(rootModel);
    if (myPathToGroovySample != null) {
      addGroovySample(rootModel);
    }
  }

  private void addGroovySample(@NotNull ModifiableRootModel rootModel) {
    Project project = rootModel.getProject();
    if (!project.isInitialized()) {
      StartupManager.getInstance(project).runAfterOpened(() -> doAddGroovySample(rootModel));
    } else {
      doAddGroovySample(rootModel);
    }
  }

  private void doAddGroovySample(@NotNull ModifiableRootModel rootModel) {
    String rootPath = getContentEntryPath();
    if (rootPath == null) {
      return;
    }
    VirtualFile root = LocalFileSystem.getInstance().refreshAndFindFileByPath(FileUtil.toSystemIndependentName(rootPath + "/" + myPathToGroovySample));
    if (root == null) {
      return;
    }
    GroovyProjectWizardUtils.createSampleGroovyCodeFile(this, rootModel.getProject(), root);
  }

  @Override
  public ModuleWizardStep[] createWizardSteps(@NotNull WizardContext wizardContext, @NotNull ModulesProvider modulesProvider) {
    return ModuleWizardStep.EMPTY_ARRAY;
  }

  @Override
  public @NonNls String getBuilderId() {
    return "groovy";
  }

  public void addGroovySample(@NotNull String path) {
    myPathToGroovySample = path;
  }

  @Override
  public Icon getNodeIcon() {
    return JetgroovyIcons.Groovy.Groovy_16x16;
  }

  @Override
  public String getDescription() {
    return GroovyBundle.message("module.with.groovy");
  }

  @Override
  public String getPresentableName() {
    return GroovyBundle.message("language.groovy");
  }

  @Override
  public String getGroupName() {
    return "Groovy";
  }

  @Override
  public String getParentGroup() {
    return "Groovy";
  }

  @Override
  public int getWeight() {
    return 60;
  }
}
