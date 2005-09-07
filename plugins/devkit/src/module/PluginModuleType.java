/*
 * Copyright 2000-2005 JetBrains s.r.o.
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

import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.ide.util.projectWizard.ProjectWizardStepFactory;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.projectRoots.ProjectJdk;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.IconLoader;
import org.jetbrains.idea.devkit.projectRoots.IdeaJdk;
import org.jetbrains.idea.devkit.DevKitBundle;

import javax.swing.*;
import java.util.ArrayList;

public class PluginModuleType extends ModuleType<PluginModuleBuilder> {
  private static final Icon PLUGIN_MODULE_ICON = IconLoader.getIcon("/general/pluginManager.png");
  private static final Icon PLUGIN_MODULE_NODE_ICON = IconLoader.getIcon("/nodes/plugin.png");
  private static final Icon ADD_PLUGIN_MODULE_ICON = IconLoader.getIcon("/add_plugin_modulewizard.png");
  private static PluginModuleType ourInstance = new PluginModuleType();

  private PluginModuleType() {
    super("PLUGIN_MODULE");
  }

  public static PluginModuleType getInstance() {
    return ourInstance;
  }

  public final boolean isJ2EE() {
    return false;
  }

  public ModuleWizardStep[] createWizardSteps(final WizardContext wizardContext,
                                              PluginModuleBuilder pluginModuleBuilder,
                                              ModulesProvider modulesProvider) {
    final ProjectWizardStepFactory stepFactory = ProjectWizardStepFactory.getInstance();
    ArrayList<ModuleWizardStep> steps = new ArrayList<ModuleWizardStep>();
    final ModuleWizardStep nameAndLocationStep = stepFactory
      .createNameAndLocationStep(wizardContext, pluginModuleBuilder, modulesProvider, ADD_PLUGIN_MODULE_ICON, "plugin.creation");
    steps.add(nameAndLocationStep);
    steps.add(stepFactory.createProjectJdkStep(wizardContext, pluginModuleBuilder, new Computable<Boolean>() {
      public Boolean compute() {
        final ProjectJdk projectJdk = wizardContext.getProjectJdk();
        return projectJdk == null || ! (projectJdk.getSdkType() instanceof IdeaJdk) ? Boolean.TRUE : Boolean.FALSE;
      }
    }, ADD_PLUGIN_MODULE_ICON, "plugin.creation")); 
    steps.add(stepFactory.createSourcePathsStep(nameAndLocationStep, pluginModuleBuilder, ADD_PLUGIN_MODULE_ICON, "plugin.creation"));
    steps.add(stepFactory.createOutputPathPathsStep(nameAndLocationStep, pluginModuleBuilder, ADD_PLUGIN_MODULE_ICON, "plugin.creation"));
    return steps.toArray(new ModuleWizardStep[steps.size()]);
  }

  public PluginModuleBuilder createModuleBuilder() {
    return new PluginModuleBuilder();
  }

  public String getName() {
    return DevKitBundle.message("module.title");
  }

  public String getDescription() {
    return DevKitBundle.message("module.description");
  }

  public Icon getBigIcon() {
    return PLUGIN_MODULE_ICON;
  }

  public Icon getNodeIcon(boolean isOpened) {
    return PLUGIN_MODULE_NODE_ICON;
  }
}