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
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.cache.CacheManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.UsageSearchContext;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xml.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.dom.*;
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

    final boolean isIdeaProject = PsiUtil.isIdeaProject(project);

    final Set<PsiClass> pluginModuleResults = checkModule(module, isIdeaProject, psiClass, registrationType);
    if (pluginModuleResults != null) {
      return pluginModuleResults;
    }

    final List<Module> candidateModules = PluginModuleType.getCandidateModules(module);
    candidateModules.remove(module);  // already checked
    for (Module m : candidateModules) {
      Set<PsiClass> types = checkModule(m, isIdeaProject, psiClass, registrationType);
      if (types != null) return types;
    }

    return null;
  }

  @Nullable
  private static Set<PsiClass> checkModule(Module module,
                                           boolean isIdeaProject,
                                           PsiClass psiClass,
                                           RegistrationType registrationType) {
    List<DomFileElement<IdeaPlugin>> pluginXmlCandidates = findPluginXmlFilesForModule(module, isIdeaProject, psiClass);
    if (pluginXmlCandidates.isEmpty()) return null;

    final String qualifiedName = psiClass.getQualifiedName();
    if (qualifiedName == null) {
      return null;
    }

    final RegistrationTypeFinder finder = new RegistrationTypeFinder(psiClass);

    for (DomFileElement<IdeaPlugin> pluginXml : pluginXmlCandidates) {
      // "main" plugin.xml
      if (!processPluginXml(pluginXml, finder, registrationType)) return finder.getTypes();

      if (isIdeaProject) continue; // pluginXmlCandidates == all candidates in module

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
            if (!processPluginXml(dependentIdeaPlugin, finder, registrationType)) return finder.getTypes();
          }
        }
      }
    }

    return finder.getTypes();
  }

  @NotNull
  private static List<DomFileElement<IdeaPlugin>> findPluginXmlFilesForModule(Module module,
                                                                              boolean isIdeaProject,
                                                                              PsiClass psiClass) {
    XmlFile pluginXml = PluginModuleType.getPluginXml(module);
    if (pluginXml != null && !isIdeaProject) {
      return Collections.singletonList(DescriptorUtil.getIdeaPlugin(pluginXml));
    }

    if (!isIdeaProject) {
      return Collections.emptyList();
    }

    final String className = psiClass.getName();
    if (className == null) {
      return Collections.emptyList();
    }

    final Project project = module.getProject();
    final DomService domService = DomService.getInstance();
    final Collection<VirtualFile> pluginXmlCandidates =
      domService.getDomFileCandidates(IdeaPlugin.class, project, GlobalSearchScope.moduleRuntimeScope(module, false));

    final VirtualFile[] pluginXmlFilesWithWord = CacheManager.SERVICE.getInstance(project)
      .getVirtualFilesWithWord(className,
                               UsageSearchContext.IN_PLAIN_TEXT,
                               GlobalSearchScope.filesWithLibrariesScope(project, pluginXmlCandidates),
                               true);

    return domService.getFileElements(IdeaPlugin.class, project,
                                      GlobalSearchScope.filesWithLibrariesScope(project, Arrays.asList(pluginXmlFilesWithWord)));
  }

  private static boolean processPluginXml(DomFileElement<IdeaPlugin> pluginXml,
                                          RegistrationTypeFinder finder,
                                          RegistrationType registrationType) {
    final IdeaPlugin rootElement = pluginXml.getRootElement();

    final boolean findAll = registrationType == RegistrationType.ALL;
    final boolean allComponents = findAll || registrationType == RegistrationType.ALL_COMPONENTS;

    if (allComponents || registrationType == RegistrationType.APPLICATION_COMPONENT) {
      if (!ContainerUtil.process(rootElement.getApplicationComponents(), components ->
        finder.processComponents(ComponentType.APPLICATION, components.getComponents()))) {
        return false;
      }
    }

    if (allComponents || registrationType == RegistrationType.PROJECT_COMPONENT) {
      if (!ContainerUtil.process(rootElement.getProjectComponents(), components ->
        finder.processComponents(ComponentType.PROJECT, components.getComponents()))) {
        return false;
      }
    }

    if (allComponents || registrationType == RegistrationType.MODULE_COMPONENT) {
      if (!ContainerUtil.process(rootElement.getModuleComponents(), components ->
        finder.processComponents(ComponentType.MODULE, components.getComponents()))) {
        return false;
      }
    }

    if (findAll || registrationType == RegistrationType.ACTION) {
      if (!ContainerUtil.process(rootElement.getActions(), actions ->
        finder.processActions(actions))) {
        return false;
      }
    }
    return true;
  }

  private static class RegistrationTypeFinder {

    private final PsiClass myPsiClass;

    private final Set<PsiClass> myTypes = ContainerUtil.newIdentityTroveSet(1);

    private RegistrationTypeFinder(PsiClass psiClass) {
      myPsiClass = psiClass;
    }

    private boolean processComponents(ComponentType type, List<? extends Component> components) {
      for (Component component : components) {
        if (!processComponent(type, component)) return false;
      }
      return true;
    }

    private boolean processComponent(ComponentType type, Component component) {
      if (myPsiClass.isEquivalentTo(component.getImplementationClass().getValue())) {
        if (addType(type.myClassName, component)) return false;
      }
      return true;
    }

    private boolean processActions(Actions actions) {
      for (Action action : actions.getActions()) {
        if (!processAction(action)) return false;
      }

      for (Group group : actions.getGroups()) {
        if (!processGroup(group)) return false;
      }
      return true;
    }

    private boolean processGroup(Group group) {
      final GenericAttributeValue<PsiClass> groupClazz = group.getClazz();
      if (matchesClass(groupClazz)) {
        if (addType(ActionType.GROUP.myClassName, group)) return false;
      }

      for (Action action : group.getActions()) {
        if (!processAction(action)) return false;
      }
      for (Group nestedGroup : group.getGroups()) {
        if (!processGroup(nestedGroup)) return false;
      }
      return true;
    }

    private boolean processAction(Action action) {
      if (matchesClass(action.getClazz())) {
        if (addType(ActionType.ACTION.myClassName, action)) return false;
      }
      return true;
    }

    private boolean matchesClass(GenericAttributeValue<PsiClass> attributeValue) {
      if (!DomUtil.hasXml(attributeValue)) return false;

      final String stringValue = attributeValue.getStringValue();
      if (stringValue == null) return false;

      // perform cheap String comparison first
      final String clazzName = stringValue.replace('$', '.');
      return clazzName.equals(myPsiClass.getQualifiedName()) &&
             myPsiClass.isEquivalentTo(attributeValue.getValue());
    }

    private boolean addType(String fqn, DomElement context) {
      final PsiClass psiClass = DomJavaUtil.findClass(fqn, context);
      ContainerUtil.addIfNotNull(myTypes, psiClass);
      return psiClass != null;
    }

    private Set<PsiClass> getTypes() {
      return myTypes.isEmpty() ? null : myTypes;
    }
  }
}
