/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import com.intellij.ide.util.projectWizard.ModuleBuilder;
import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.platform.ProjectTemplate;
import com.intellij.platform.ProjectTemplatesFactory;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Dmitry Avdeev
 *         Date: 11/9/12
 */
public class AndroidProjectTemplatesFactory implements ProjectTemplatesFactory {

  public static final String ANDROID = "Android";

  @NotNull
  @Override
  public String[] getGroups() {
    return new String[] {ANDROID};
  }

  @NotNull
  @Override
  public ProjectTemplate[] createTemplates(String group, WizardContext context) {
    ProjectTemplate[] templates = {
      new AndroidProjectTemplate("Empty Android Module",
                                 "Simple <b>Android</b> module with configured Android SDK and without any pre-defined structure",
                                 new AndroidModuleBuilder(null) {

                                   @Override
                                   public String getBuilderId() {
                                     return "android.empty";
                                   }

                                   @Override
                                   public ModuleWizardStep[] createWizardSteps(WizardContext wizardContext,
                                                                               ModulesProvider modulesProvider) {
                                     return ModuleWizardStep.EMPTY_ARRAY;
                                   }
                                 }),

      new AndroidProjectTemplate("Android Library Module",
                                 "",
                                 new AndroidModuleBuilder.Library())
    };
    if (context.getProject() == null) {
      return templates;
    }
    else {
      AndroidProjectTemplate test = new AndroidProjectTemplate("Android Test Module", "", new AndroidModuleBuilder.Test());
      return ArrayUtil.append(templates, test);
    }
  }

  private static class AndroidProjectTemplate implements ProjectTemplate {

    private final String myName;
    private final String myDescription;
    private final AndroidModuleBuilder myBuilder;

    private AndroidProjectTemplate(String name, String description, AndroidModuleBuilder builder) {
      myName = name;
      myDescription = description;
      myBuilder = builder;
    }

    @NotNull
    @Override
    public String getName() {
      return myName;
    }

    @Nullable
    @Override
    public String getDescription() {
      return myDescription;
    }

    @NotNull
    @Override
    public ModuleBuilder createModuleBuilder() {
      return myBuilder;
    }

    @Nullable
    @Override
    public ValidationInfo validateSettings() {
      return null;
    }
  }
}
