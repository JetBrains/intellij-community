/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.mvc;

import com.intellij.ide.util.projectWizard.ModuleBuilder;
import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.ide.util.projectWizard.SettingsStep;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.module.JavaModuleType;
import com.intellij.openapi.options.ConfigurationException;
import org.jetbrains.annotations.Nullable;

/**
* @author nik
*/
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
      settingsStep.addSettingsField("\u001B" + (framework == null ? "Groovy" : framework.getDisplayName()) + " library:", getPanel().getSimplePanel());
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
