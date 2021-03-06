// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.config;

import com.intellij.ide.util.projectWizard.JavaModuleBuilder;
import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.ide.util.projectWizard.SettingsStep;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import icons.JetgroovyIcons;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyBundle;

import javax.swing.*;

/**
 * @author peter
 */
public final class GroovyAwareModuleBuilder extends JavaModuleBuilder {

  @Override
  public ModuleWizardStep modifyProjectTypeStep(@NotNull SettingsStep settingsStep) {
    return new GroovySdkForNewModuleWizardStep(this, settingsStep);
  }

  @Override
  public ModuleWizardStep[] createWizardSteps(@NotNull WizardContext wizardContext, @NotNull ModulesProvider modulesProvider) {
    return ModuleWizardStep.EMPTY_ARRAY;
  }

  @Override
  public @NonNls String getBuilderId() {
    return "groovy";
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
