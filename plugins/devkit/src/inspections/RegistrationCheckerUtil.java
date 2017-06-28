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
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.paths.PathReference;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlTagValue;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xml.DomFileElement;
import com.intellij.util.xml.DomService;
import com.intellij.util.xml.DomUtil;
import com.intellij.util.xml.GenericAttributeValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.dom.Dependency;
import org.jetbrains.idea.devkit.dom.IdeaPlugin;
import org.jetbrains.idea.devkit.module.PluginModuleType;
import org.jetbrains.idea.devkit.util.ActionType;
import org.jetbrains.idea.devkit.util.ComponentType;
import org.jetbrains.idea.devkit.util.DescriptorUtil;
import org.jetbrains.idea.devkit.util.PsiUtil;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

class RegistrationCheckerUtil {

  @Nullable
  static Set<PsiClass> getRegistrationTypes(PsiClass psiClass, boolean includeActions) {
    final Project project = psiClass.getProject();
    final PsiFile psiFile = psiClass.getContainingFile();

    assert psiFile != null;

    final VirtualFile virtualFile = psiFile.getVirtualFile();
    if (virtualFile == null) return null;
    final Module module = ModuleUtilCore.findModuleForFile(virtualFile, project);

    if (module == null) return null;

    final boolean isIdeaProject = PsiUtil.isIdeaProject(project);

    if (PluginModuleType.isOfType(module) ||
        PsiUtil.isPluginModule(module)) {
      final Set<PsiClass> pluginModuleResults = checkModule(module, psiClass, includeActions);
      if (!isIdeaProject && pluginModuleResults != null) {
        return pluginModuleResults;
      }
    }

    final List<Module> candidateModules = PluginModuleType.getCandidateModules(module);
    candidateModules.remove(module);  // already checked
    for (Module m : candidateModules) {
      Set<PsiClass> types = checkModule(m, psiClass, includeActions);
      if (types != null) return types;
    }

    // fallback in IJ project: all modules
    if (isIdeaProject) {
      for (Module m : ModuleManager.getInstance(module.getProject()).getModules()) {
        if (candidateModules.contains(m)) continue;
        Set<PsiClass> types = checkModule(m, psiClass, includeActions);
        if (types != null) {
          return types;
        }
      }
    }
    return null;
  }

  @Nullable
  private static Set<PsiClass> checkModule(Module module, PsiClass psiClass, boolean includeActions) {
    List<XmlFile> pluginXmlCandidates = findPluginXmlFilesForModule(module);
    if (pluginXmlCandidates.isEmpty()) return null;

    final String qualifiedName = psiClass.getQualifiedName();
    if (qualifiedName == null) {
      return null;
    }

    final RegistrationTypeFinder finder = new RegistrationTypeFinder(psiClass, null);

    for (XmlFile pluginXml : pluginXmlCandidates) {
      final DomFileElement<IdeaPlugin> fileElement = DescriptorUtil.getIdeaPlugin(pluginXml);
      if (fileElement == null) continue;

      // "main" plugin.xml
      processPluginXml(pluginXml, finder, includeActions);

      // <depends> plugin.xml files
      for (Dependency dependency : fileElement.getRootElement().getDependencies()) {
        final GenericAttributeValue<PathReference> configFileAttribute = dependency.getConfigFile();
        if (!DomUtil.hasXml(configFileAttribute)) continue;

        final PathReference configFile = configFileAttribute.getValue();
        if (configFile != null) {
          final PsiElement resolve = configFile.resolve();
          if (!(resolve instanceof XmlFile)) continue;
          final XmlFile depPluginXml = (XmlFile)resolve;
          if (pluginXmlCandidates.contains(depPluginXml)) continue; // already processed

          if (DescriptorUtil.isPluginXml(depPluginXml)) {
            processPluginXml(depPluginXml, finder, includeActions);
          }
        }
      }
    }

    return finder.getTypes();
  }

  @NotNull
  private static List<XmlFile> findPluginXmlFilesForModule(Module module) {
    final Project project = module.getProject();
    final boolean isIdeaProject = PsiUtil.isIdeaProject(project);

    XmlFile pluginXml = PluginModuleType.getPluginXml(module);
    if (pluginXml != null && !isIdeaProject) {
      return Collections.singletonList(pluginXml);
    }

    if (!isIdeaProject) {
      return Collections.emptyList();
    }

    final Collection<VirtualFile> candidates =
      DomService.getInstance().getDomFileCandidates(IdeaPlugin.class, project,
                                                    GlobalSearchScope.moduleRuntimeScope(module, false));
    return ContainerUtil.findAll(PsiUtilCore.toPsiFiles(PsiManager.getInstance(project), candidates),
                                 XmlFile.class);
  }

  private static void processPluginXml(XmlFile xmlFile, RegistrationTypeFinder finder, boolean includeActions) {
    final XmlDocument document = xmlFile.getDocument();
    if (document == null) return;
    final XmlTag rootTag = document.getRootTag();
    if (rootTag == null) return;

    DescriptorUtil.processComponents(rootTag, finder);
    if (includeActions) {
      DescriptorUtil.processActions(rootTag, finder);
    }
  }

  private static class RegistrationTypeFinder implements ComponentType.Processor, ActionType.Processor {

    private Set<PsiClass> myTypes;
    private final String myQualifiedName;
    private final PsiManager myManager;
    private final GlobalSearchScope myScope;

    private RegistrationTypeFinder(PsiClass psiClass, Set<PsiClass> types) {
      myTypes = types;
      myQualifiedName = psiClass.getQualifiedName();
      myManager = psiClass.getManager();
      myScope = psiClass.getResolveScope();
    }

    public boolean process(ComponentType type, XmlTag component, XmlTagValue impl, XmlTagValue intf) {
      if (impl != null && myQualifiedName.equals(fixClassName(impl.getTrimmedText()))) {
        final PsiClass clazz = JavaPsiFacade.getInstance(myManager.getProject()).findClass(type.myClassName, myScope);
        if (clazz != null) {
          addType(clazz);
        }
      }
      return true;
    }

    public boolean process(ActionType type, XmlTag action) {
      final String actionClass = action.getAttributeValue("class");
      if (actionClass != null) {
        if (fixClassName(actionClass).equals(myQualifiedName)) {
          final PsiClass clazz = JavaPsiFacade.getInstance(myManager.getProject()).findClass(type.myClassName, myScope);
          if (clazz != null) {
            addType(clazz);
            return false;
          }
        }
      }
      return true;
    }

    private static String fixClassName(String xmlValue) {
      return xmlValue.trim().replace('$', '.');
    }

    private void addType(PsiClass clazz) {
      if (myTypes == null) {
        //noinspection unchecked
        myTypes = ContainerUtil.newIdentityTroveSet(2);
      }
      myTypes.add(clazz);
    }

    public Set<PsiClass> getTypes() {
      return myTypes;
    }
  }
}
