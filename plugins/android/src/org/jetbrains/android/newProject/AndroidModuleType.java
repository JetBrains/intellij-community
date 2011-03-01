/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

package org.jetbrains.android.newProject;

import com.intellij.CommonBundle;
import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.ide.util.projectWizard.ProjectJdkForModuleStep;
import com.intellij.ide.util.projectWizard.ProjectWizardStepFactory;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.module.ModuleTypeManager;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.openapi.ui.Messages;
import org.jetbrains.android.sdk.AndroidSdkUtils;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.android.util.AndroidUtils;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: Eugene.Kudelevsky
 * Date: Jun 26, 2009
 * Time: 7:30:12 PM
 * To change this template use File | Settings | File Templates.
 */
public class AndroidModuleType extends ModuleType<AndroidModuleBuilder> {
  public static final String MODULE_TYPE_ID = "ANDROID_MODULE";

  public AndroidModuleType() {
    super(MODULE_TYPE_ID);
  }

  public static AndroidModuleType getInstance() {
    return (AndroidModuleType)ModuleTypeManager.getInstance().findByID(MODULE_TYPE_ID);
  }

  public AndroidModuleBuilder createModuleBuilder() {
    return new AndroidModuleBuilder();
  }

  @Override
  public ModuleWizardStep[] createWizardSteps(final WizardContext wizardContext,
                                              final AndroidModuleBuilder moduleBuilder,
                                              ModulesProvider modulesProvider) {
    List<ModuleWizardStep> steps = new ArrayList<ModuleWizardStep>();
    ProjectWizardStepFactory factory = ProjectWizardStepFactory.getInstance();
    steps.add(factory.createSourcePathsStep(wizardContext, moduleBuilder, null, "reference.dialogs.new.project.fromScratch.source"));

    if (!hasAppropriateJdk()) {
      steps.add(new ProjectJdkForModuleStep(wizardContext, JavaSdk.getInstance()) {
        @Override
        public void updateDataModel() {
          // do nothing
        }

        @Override
        public boolean validate() {
          for (Object o : getAllJdks()) {
            if (o instanceof Sdk) {
              Sdk sdk = (Sdk)o;
              if (AndroidSdkUtils.isApplicableJdk(sdk)) {
                return true;
              }
            }
          }
          Messages.showErrorDialog(AndroidBundle.message("no.jdk.error"), CommonBundle.getErrorTitle());
          return false;
        }
      });
    }

    steps.add(new AndroidModuleWizardStep(moduleBuilder, wizardContext));
    return steps.toArray(new ModuleWizardStep[steps.size()]);
  }

  private static boolean hasAppropriateJdk() {
    for (Sdk sdk : ProjectJdkTable.getInstance().getAllJdks()) {
      if (AndroidSdkUtils.isApplicableJdk(sdk)) {
        return true;
      }
    }
    return false;
  }

  public String getName() {
    return AndroidBundle.message("android.module.type.name");
  }

  public String getDescription() {
    return AndroidBundle.message("android.module.type.description");
  }

  public Icon getBigIcon() {
    return AndroidUtils.ANDROID_ICON_24;
  }

  public Icon getNodeIcon(boolean isOpened) {
    return AndroidUtils.ANDROID_ICON;
  }
}
