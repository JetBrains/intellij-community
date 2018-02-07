/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package org.jetbrains.idea.devkit.inspections;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.paths.PathReference;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.include.FileIncludeManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.GlobalSearchScopesCore;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xml.DomFileElement;
import com.intellij.util.xml.DomUtil;
import com.intellij.util.xml.GenericAttributeValue;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.dom.Dependency;
import org.jetbrains.idea.devkit.dom.IdeaPlugin;
import org.jetbrains.idea.devkit.dom.index.IdeaPluginRegistrationIndex;
import org.jetbrains.idea.devkit.module.PluginModuleType;
import org.jetbrains.idea.devkit.util.ActionType;
import org.jetbrains.idea.devkit.util.ComponentType;
import org.jetbrains.idea.devkit.util.DescriptorUtil;
import org.jetbrains.idea.devkit.util.PsiUtil;

import java.util.*;

class RegistrationCheckerUtil {

  enum RegistrationType {
    ALL,
    ALL_COMPONENTS,
    APPLICATION_COMPONENT,
    PROJECT_COMPONENT,
    MODULE_COMPONENT,
    ACTION
  }

  @Nullable
  static Set<PsiClass> getRegistrationTypes(PsiClass psiClass, RegistrationType registrationType) {
    final Project project = psiClass.getProject();
    final PsiFile psiFile = psiClass.getContainingFile();

    assert psiFile != null;

    final VirtualFile virtualFile = psiFile.getVirtualFile();
    if (virtualFile == null) return null;
    final Module module = ModuleUtilCore.findModuleForFile(virtualFile, project);
    if (module == null) return null;

    final RegistrationTypeFinder finder = new RegistrationTypeFinder(psiClass, registrationType);

    if (PsiUtil.isIdeaProject(project)) {
      return checkIdeaProject(project, finder);
    }

    final Set<PsiClass> pluginModuleResults = checkModule(module, finder);
    if (pluginModuleResults != null) {
      return pluginModuleResults;
    }

    final List<Module> candidateModules = PluginModuleType.getCandidateModules(module);
    candidateModules.remove(module);  // already checked
    for (Module candidateModule : candidateModules) {
      Set<PsiClass> types = checkModule(candidateModule, finder);
      if (types != null) return types;
    }

    return null;
  }

  @Nullable
  private static Set<PsiClass> checkIdeaProject(Project project,
                                                RegistrationTypeFinder finder) {
    finder.processScope(GlobalSearchScopesCore.projectProductionScope(project));
    return finder.getTypes();
  }

  @Nullable
  private static Set<PsiClass> checkModule(Module module,
                                           RegistrationTypeFinder finder) {
    final DomFileElement<IdeaPlugin> pluginXml = getPluginXmlFile(module);
    if (pluginXml == null) {
      return null;
    }

    // "main" plugin.xml
    XmlFile pluginXmlFile = pluginXml.getFile();
    if (!finder.processScope(GlobalSearchScope.fileScope(pluginXmlFile))) {
      return finder.getTypes();
    }

    Set<PsiFile> processedFiles = new HashSet<>();
    processedFiles.add(pluginXmlFile);

    // <depends> plugin.xml files
    for (Dependency dependency : pluginXml.getRootElement().getDependencies()) {
      final GenericAttributeValue<PathReference> configFileAttribute = dependency.getConfigFile();
      if (!DomUtil.hasXml(configFileAttribute)) continue;

      final PathReference configFile = configFileAttribute.getValue();
      if (configFile != null) {
        final PsiElement resolve = configFile.resolve();
        if (!(resolve instanceof XmlFile)) continue;
        final XmlFile depPluginXml = (XmlFile)resolve;

        final DomFileElement<IdeaPlugin> dependentIdeaPlugin = DescriptorUtil.getIdeaPlugin(depPluginXml);
        if (dependentIdeaPlugin != null) {
          if (!finder.processScope(GlobalSearchScope.fileScope(dependentIdeaPlugin.getFile()))) {
            return finder.getTypes();
          }
        }
        processedFiles.add(depPluginXml);
      }
    }

    Project project = module.getProject();
    PsiManager psiManager = PsiManager.getInstance(project);
    FileIncludeManager includeManager = FileIncludeManager.getManager(project);
    Set<PsiFile> processedIncludedFiles = new HashSet<>();
    for (PsiFile file : processedFiles) { // main plugin.xml + dependents
      VirtualFile[] includes = includeManager.getIncludedFiles(file.getVirtualFile(), true, true);
      for (VirtualFile includedFile : includes) {
        PsiFile includedPsiFile = psiManager.findFile(includedFile);
        if (includedPsiFile == null) {
          continue;
        }
        if (processedFiles.contains(includedPsiFile) || !processedIncludedFiles.add(includedPsiFile)) {
          continue;
        }
        if (!finder.processScope(GlobalSearchScope.fileScope(includedPsiFile))) {
          return finder.getTypes();
        }
      }
    }

    return finder.getTypes();
  }

  @Nullable
  private static DomFileElement<IdeaPlugin> getPluginXmlFile(Module module) {
    XmlFile pluginXml = PluginModuleType.getPluginXml(module);
    if (pluginXml == null) {
      return null;
    }
    return DescriptorUtil.getIdeaPlugin(pluginXml);
  }


  private static class RegistrationTypeFinder {

    private final PsiClass myPsiClass;
    private final RegistrationType myRegistrationType;

    private final Set<PsiClass> myTypes = ContainerUtil.newIdentityTroveSet(1);

    private RegistrationTypeFinder(PsiClass psiClass, RegistrationType registrationType) {
      myPsiClass = psiClass;
      myRegistrationType = registrationType;
    }

    private boolean processScope(GlobalSearchScope scope) {
      final boolean findAll = myRegistrationType == RegistrationType.ALL;
      final boolean allComponents = findAll || myRegistrationType == RegistrationType.ALL_COMPONENTS;

      if (allComponents || myRegistrationType == RegistrationType.APPLICATION_COMPONENT) {
        if (IdeaPluginRegistrationIndex.isRegisteredApplicationComponent(myPsiClass, scope)) {
          addType(ComponentType.APPLICATION.myClassName);
          return false;
        }
      }
      if (allComponents || myRegistrationType == RegistrationType.PROJECT_COMPONENT) {
        if (IdeaPluginRegistrationIndex.isRegisteredProjectComponent(myPsiClass, scope)) {
          addType(ComponentType.PROJECT.myClassName);
          return false;
        }
      }
      if (allComponents || myRegistrationType == RegistrationType.MODULE_COMPONENT) {
        if (IdeaPluginRegistrationIndex.isRegisteredModuleComponent(myPsiClass, scope)) {
          addType(ComponentType.MODULE.myClassName);
          return false;
        }
      }

      if (findAll || myRegistrationType == RegistrationType.ACTION) {
        if (IdeaPluginRegistrationIndex.isRegisteredAction(myPsiClass,
                                                           scope)) {
          addType(ActionType.ACTION.myClassName);
          return false;
        }
      }
      return true;
    }

    private void addType(String fqn) {
      final PsiClass psiClass = JavaPsiFacade.getInstance(myPsiClass.getProject())
        .findClass(fqn, myPsiClass.getResolveScope());
      ContainerUtil.addIfNotNull(myTypes, psiClass);
    }

    private Set<PsiClass> getTypes() {
      return myTypes.isEmpty() ? null : myTypes;
    }
  }
}
