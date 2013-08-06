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
package org.jetbrains.plugins.groovy.config;

import com.intellij.ide.util.projectWizard.JavaModuleBuilder;
import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import icons.JetgroovyIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.mvc.GroovySdkForNewModuleWizardStep;
import org.jetbrains.plugins.groovy.mvc.MvcFramework;

import javax.swing.*;

/**
 * @author peter
 */
public class GroovyAwareModuleBuilder extends JavaModuleBuilder {
  private final String myBuilderId;
  private final String myPresentableName;
  private final String myDescription;
  private final Icon myBigIcon;

  @SuppressWarnings("UnusedDeclaration")
  public GroovyAwareModuleBuilder() {
    this("groovy", "Groovy Module", "Simple module with attached Groovy library", null);
  }

  protected GroovyAwareModuleBuilder(String builderId, String presentableName, String description, Icon bigIcon) {
    myBuilderId = builderId;
    myPresentableName = presentableName;
    myDescription = description;
    myBigIcon = bigIcon;
  }

  @Override
  public ModuleWizardStep[] createWizardSteps(@NotNull WizardContext wizardContext, @NotNull ModulesProvider modulesProvider) {
    return new ModuleWizardStep[]{new GroovySdkForNewModuleWizardStep(this, wizardContext, getFramework())};
  }

  //@Nullable
  //@Override
  //public ModuleWizardStep modifySettingsStep(SettingsStep settingsStep) {
  //  return new GroovySdkForNewModuleWizardStep(this, settingsStep.getContext(), getFramework());
  //}
  //
  @Override
  public String getBuilderId() {
    return myBuilderId;
  }

  @Override
  public Icon getBigIcon() {
    return myBigIcon;
  }

  @Override
  public Icon getNodeIcon() {
    return JetgroovyIcons.Groovy.Groovy_16x16;
  }

  @Override
  public String getDescription() {
    return myDescription;
  }

  @Override
  public String getPresentableName() {
    return myPresentableName;
  }

  @Override
  public String getGroupName() {
    return "Groovy";
  }

  protected MvcFramework getFramework() {
    return null;
  }
}
