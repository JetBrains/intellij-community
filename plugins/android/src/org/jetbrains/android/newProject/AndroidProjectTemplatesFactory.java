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

import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.platform.ProjectTemplate;
import com.intellij.platform.ProjectTemplatesFactory;
import com.intellij.platform.templates.BuilderBasedTemplate;
import com.intellij.util.ArrayUtil;
import icons.AndroidIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author Dmitry Avdeev
 *         Date: 11/9/12
 */
public class AndroidProjectTemplatesFactory extends ProjectTemplatesFactory {

  public static final String ANDROID = "Android";
  public static final String EMPTY_MODULE = "Empty Module";
  public static final String LIBRARY_MODULE = "Library Module";
  public static final String TEST_MODULE = "Test Module";

  @NotNull
  @Override
  public String[] getGroups() {
    return new String[] {ANDROID};
  }

  @Override
  public Icon getGroupIcon(String group) {
    return AndroidIcons.Android;
  }

  @NotNull
  @Override
  public ProjectTemplate[] createTemplates(String group, WizardContext context) {
    ProjectTemplate[] templates = {
      new BuilderBasedTemplate(new AndroidModuleBuilder()),
      new AndroidProjectTemplate(EMPTY_MODULE,
                                 "Simple Android module with configured Android SDK and without any pre-defined structure",
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

      new AndroidProjectTemplate(LIBRARY_MODULE,
                                 "Android library modules hold shared Android source code and resources " +
                                 "that can be referenced by other Android modules",
                                 new AndroidModuleBuilder.Library())
    };
    if (context.getProject() == null) {
      return templates;
    }
    else {
      AndroidProjectTemplate test = new AndroidProjectTemplate(TEST_MODULE,
                                                               "Android test modules contain Android applications that you write using " +
                                                               "the <a href='http://developer.android.com/tools/testing/index.html'>" +
                                                               "Testing and Instrumentation framework</a>",
                                                               new AndroidModuleBuilder.Test());
      return ArrayUtil.append(templates, test);
    }
  }

  private static class AndroidProjectTemplate extends BuilderBasedTemplate {

    private final String myName;
    private final String myDescription;

    private AndroidProjectTemplate(String name, String description, AndroidModuleBuilder builder) {
      super(builder);
      myName = name;
      myDescription = description;
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
  }
}
