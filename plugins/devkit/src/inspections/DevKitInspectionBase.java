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
package org.jetbrains.idea.devkit.inspections;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.xml.*;
import gnu.trove.THashSet;
import gnu.trove.TObjectHashingStrategy;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.DevKitBundle;
import org.jetbrains.idea.devkit.module.PluginModuleType;
import org.jetbrains.idea.devkit.util.ActionType;
import org.jetbrains.idea.devkit.util.ComponentType;
import org.jetbrains.idea.devkit.util.DescriptorUtil;

import java.util.List;
import java.util.Set;

/**
 * @author swr
 */
public abstract class DevKitInspectionBase extends LocalInspectionTool {

  public String getGroupDisplayName() {
    return DevKitBundle.message("inspections.group.name");
  }

  @Nullable
  protected static Set<PsiClass> getRegistrationTypes(PsiClass psiClass, boolean includeActions) {
    final Project project = psiClass.getProject();
    final PsiFile psiFile = psiClass.getContainingFile();

    assert psiFile != null;

    final VirtualFile virtualFile = psiFile.getVirtualFile();
    if (virtualFile == null) return null;
    final Module module = VfsUtil.getModuleForFile(project, virtualFile);

    if (module == null) return null;

    if (PluginModuleType.isOfType(module)) {
      return checkModule(module, psiClass, null, includeActions);
    } else {
      Set<PsiClass> types = null;
      final List<Module> modules = PluginModuleType.getCandidateModules(module);
      for (Module m : modules) {
        types = checkModule(m, psiClass, types, includeActions);
      }
      return types;
    }
  }

  private static Set<PsiClass> checkModule(Module module, PsiClass psiClass, Set<PsiClass> types, boolean includeActions) {
    final XmlFile pluginXml = PluginModuleType.getPluginXml(module);
    if (!isPluginXml(pluginXml)) return types;
    assert pluginXml != null;

    final XmlDocument document = pluginXml.getDocument();
    assert document != null;

    final XmlTag rootTag = document.getRootTag();
    assert rootTag != null;

    final String qualifiedName = psiClass.getQualifiedName();
    if (qualifiedName != null) {
      final RegistrationTypeFinder finder = new RegistrationTypeFinder(psiClass, types);

      DescriptorUtil.processComponents(rootTag, finder);

      if (includeActions) {
        DescriptorUtil.processActions(rootTag, finder);
      }

      types = finder.getTypes();
    }

    return types;
  }

  protected static boolean isPluginXml(PsiFile file) {
    if (!(file instanceof XmlFile)) return false;
    final XmlFile pluginXml = (XmlFile)file;

    final XmlDocument document = pluginXml.getDocument();
    if (document == null) return false;
    final XmlTag rootTag = document.getRootTag();
    return rootTag != null && "idea-plugin".equals(rootTag.getLocalName());

  }

  @Nullable
  protected static PsiElement getAttValueToken(@NotNull XmlAttribute attribute) {
    final XmlAttributeValue valueElement = attribute.getValueElement();
    if (valueElement == null) return null;

    final PsiElement[] children = valueElement.getChildren();
    if (children.length == 3 && children[1] instanceof XmlToken) {
      return children[1];
    }
    return valueElement;
  }

  protected static boolean isAbstract(PsiModifierListOwner checkedClass) {
    return checkedClass.hasModifierProperty(PsiModifier.ABSTRACT);
  }

  protected static boolean isPublic(PsiModifierListOwner checkedClass) {
    return checkedClass.hasModifierProperty(PsiModifier.PUBLIC);
  }

  protected static boolean isActionRegistered(PsiClass psiClass) {
    final Set<PsiClass> registrationTypes = getRegistrationTypes(psiClass, true);
    if (registrationTypes != null) {
      for (PsiClass type : registrationTypes) {
        if (AnAction.class.getName().equals(type.getQualifiedName())) return true;
        if (ActionGroup.class.getName().equals(type.getQualifiedName())) return true;
      }
    }
    return false;
  }

  static class RegistrationTypeFinder implements ComponentType.Processor, ActionType.Processor {
    private Set<PsiClass> myTypes;
    private final String myQualifiedName;
    private final PsiManager myManager;
    private final GlobalSearchScope myScope;

    public RegistrationTypeFinder(PsiClass psiClass, Set<PsiClass> types) {
      myTypes = types;
      myQualifiedName = psiClass.getQualifiedName();
      myManager = psiClass.getManager();
      myScope = psiClass.getResolveScope();
    }

    public boolean process(ComponentType type, XmlTag component, XmlTagValue impl, XmlTagValue intf) {
      if (impl != null && myQualifiedName.equals(impl.getTrimmedText())) {
        final PsiClass clazz = myManager.findClass(type.myClassName, myScope);
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
          final PsiClass clazz = myManager.findClass(type.myClassName, myScope);
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
        myTypes = new THashSet<PsiClass>(2, TObjectHashingStrategy.IDENTITY);
      }
      myTypes.add(clazz);
    }

    public Set<PsiClass> getTypes() {
      return myTypes;
    }
  }
}
