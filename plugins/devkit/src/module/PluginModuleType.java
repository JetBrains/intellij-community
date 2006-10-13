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
import com.intellij.javaee.make.ModuleBuildProperties;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.projectRoots.ProjectJdk;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.pointers.VirtualFilePointer;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.HashSet;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.DevKitBundle;
import org.jetbrains.idea.devkit.build.PluginBuildUtil;
import org.jetbrains.idea.devkit.build.PluginModuleBuildProperties;
import org.jetbrains.idea.devkit.projectRoots.IdeaJdk;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class PluginModuleType extends ModuleType<PluginModuleBuilder> {
  private static final Icon PLUGIN_MODULE_ICON = IconLoader.getIcon("/modules/pluginModule.png");
  private static final Icon PLUGIN_MODULE_NODE_ICON = IconLoader.getIcon("/nodes/plugin.png");
  private static final Icon ADD_PLUGIN_MODULE_ICON = IconLoader.getIcon("/add_plugin_modulewizard.png");
  private static PluginModuleType ourInstance = new PluginModuleType();

  private PluginModuleType() {
    super("PLUGIN_MODULE");
  }

  public static PluginModuleType getInstance() {
    return ourInstance;
  }

  public static boolean isOfType(Module module) {
    return module.getModuleType() == ourInstance;
  }

  public final boolean isJ2EE() {
    return false;
  }

  public ModuleWizardStep[] createWizardSteps(final WizardContext wizardContext,
                                              PluginModuleBuilder moduleBuilder,
                                              ModulesProvider modulesProvider) {
    final ProjectWizardStepFactory stepFactory = ProjectWizardStepFactory.getInstance();
    ArrayList<ModuleWizardStep> steps = new ArrayList<ModuleWizardStep>();
    final ModuleWizardStep nameAndLocationStep = stepFactory
      .createNameAndLocationStep(wizardContext, moduleBuilder, modulesProvider, ADD_PLUGIN_MODULE_ICON, "plugin.creation");
    steps.add(nameAndLocationStep);
    steps.add(stepFactory.createProjectJdkStep(wizardContext, ApplicationManager.getApplication().getComponent(IdeaJdk.class), moduleBuilder, new Computable<Boolean>() {
      public Boolean compute() {
        final ProjectJdk projectJdk = wizardContext.getProjectJdk();
        return projectJdk == null || ! (projectJdk.getSdkType() instanceof IdeaJdk) ? Boolean.TRUE : Boolean.FALSE;
      }
    }, ADD_PLUGIN_MODULE_ICON, "plugin.creation"));
    steps.add(stepFactory.createSourcePathsStep(nameAndLocationStep, moduleBuilder, ADD_PLUGIN_MODULE_ICON, "plugin.creation"));
    final ModuleWizardStep[] wizardSteps = steps.toArray(new ModuleWizardStep[steps.size()]);
    return ArrayUtil.mergeArrays(wizardSteps, super.createWizardSteps(wizardContext, moduleBuilder, modulesProvider), ModuleWizardStep.class);
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

  @Nullable
  public static XmlFile getPluginXml(Module module) {
    if (module == null) return null;
    if (module.getModuleType() != ourInstance) return null;

    final ModuleBuildProperties buildProperties = module.getComponent(ModuleBuildProperties.class);
    if (!(buildProperties instanceof PluginModuleBuildProperties)) return null;
    final VirtualFilePointer pluginXMLPointer = ((PluginModuleBuildProperties)buildProperties).getPluginXMLPointer();
    final VirtualFile vFile = pluginXMLPointer.getFile();
    if (vFile == null) return null;
    final PsiFile file = PsiManager.getInstance(module.getProject()).findFile(vFile);
    return file instanceof XmlFile ? (XmlFile)file : null;
}

  public static boolean isPluginModuleOrDependency(@NotNull Module module) {
    if (isOfType(module)) return true;

    return getCandidateModules(module).size() > 0;
  }

  public static List<Module> getCandidateModules(Module module) {
    final ModuleRootManager manager = ModuleRootManager.getInstance(module);

    final ProjectJdk jdk = manager.getJdk();
    // don't allow modules that don't use an IDEA-JDK
    if (jdk == null || !(jdk.getSdkType() instanceof IdeaJdk)) {
      //noinspection unchecked
      return Collections.emptyList();
    }

    final Module[] modules = ModuleManager.getInstance(module.getProject()).getModules();
    final List<Module> candidates = new ArrayList<Module>(modules.length);
    final Set<Module> deps = new HashSet<Module>(modules.length);
    for (Module m : modules) {
      if (m.getModuleType() == getInstance()) {
        deps.clear();
        PluginBuildUtil.getDependencies(m, deps);

        if (deps.contains(module) && getPluginXml(m) != null) {
          candidates.add(m);
        }
      }
    }
    return candidates;
  }
}