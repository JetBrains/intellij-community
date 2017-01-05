/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlTagValue;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xml.DomFileElement;
import com.intellij.util.xml.DomUtil;
import com.intellij.util.xml.GenericAttributeValue;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.dom.Dependency;
import org.jetbrains.idea.devkit.dom.IdeaPlugin;
import org.jetbrains.idea.devkit.module.PluginModuleType;
import org.jetbrains.idea.devkit.util.ActionType;
import org.jetbrains.idea.devkit.util.ComponentType;
import org.jetbrains.idea.devkit.util.DescriptorUtil;

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

    if (PluginModuleType.isOfType(module)) {
      return checkModule(module, psiClass, null, includeActions);
    }
    else {
      Set<PsiClass> types = null;
      final List<Module> modules = PluginModuleType.getCandidateModules(module);
      for (Module m : modules) {
        types = checkModule(m, psiClass, types, includeActions);
      }
      return types;
    }
  }

  @Nullable
  private static Set<PsiClass> checkModule(Module module, PsiClass psiClass, @Nullable Set<PsiClass> types, boolean includeActions) {
    final XmlFile pluginXml = PluginModuleType.getPluginXml(module);
    if (pluginXml == null) return null;
    final DomFileElement<IdeaPlugin> fileElement = DescriptorUtil.getIdeaPlugin(pluginXml);
    if (fileElement == null) return null;

    final String qualifiedName = psiClass.getQualifiedName();
    if (qualifiedName != null) {
      final RegistrationTypeFinder finder = new RegistrationTypeFinder(psiClass, types);

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
          if (DescriptorUtil.isPluginXml(depPluginXml)) {
            processPluginXml(depPluginXml, finder, includeActions);
          }
        }
      }

      types = finder.getTypes();
    }

    return types;
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
      if (impl != null && myQualifiedName.equals(impl.getTrimmedText())) {
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
        if (actionClass.trim().equals(myQualifiedName)) {
          final PsiClass clazz = JavaPsiFacade.getInstance(myManager.getProject()).findClass(type.myClassName, myScope);
          if (clazz != null) {
            addType(clazz);
            return false;
          }
        }
      }
      return true;
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
