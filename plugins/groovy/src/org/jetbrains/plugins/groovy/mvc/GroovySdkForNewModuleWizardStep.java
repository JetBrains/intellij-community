// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.mvc;

import com.intellij.ide.util.projectWizard.ModuleBuilder;
import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.ide.util.projectWizard.SettingsStep;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.module.JavaModuleType;
import com.intellij.openapi.options.ConfigurationException;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyBundle;

public class GroovySdkForNewModuleWizardStep extends GroovySdkWizardStepBase {

  @Nullable
  private ModuleWizardStep myJavaStep;

  public GroovySdkForNewModuleWizardStep(ModuleBuilder moduleBuilder,
                                         WizardContext wizardContext,
                                         @Nullable MvcFramework framework,
                                         SettingsStep settingsStep) {
    super(framework, wizardContext, moduleBuilder.getContentEntryPath());
    moduleBuilder.addModuleConfigurationUpdater(createModuleConfigurationUpdater());
    if (settingsStep != null) {
      myJavaStep = JavaModuleType.getModuleType().modifyProjectTypeStep(settingsStep, moduleBuilder);
      settingsStep.addSettingsField(
        GroovyBundle.message(
          "mvc.framework.0.library.label",
          framework == null ? GroovyBundle.message("language.groovy")
                            : framework.getDisplayName()
        ), getPanel().getSimplePanel());
    }
  }

  @Override
  public boolean validate() throws ConfigurationException {
    return super.validate() && (myJavaStep == null || myJavaStep.validate());
  }

  @Override
  public void updateDataModel() {
    super.updateDataModel();
    if (myJavaStep != null) {
      myJavaStep.updateDataModel();
    }
  }
}
