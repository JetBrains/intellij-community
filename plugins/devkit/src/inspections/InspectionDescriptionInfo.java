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

import com.intellij.codeInspection.InspectionEP;
import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.util.Query;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomFileElement;
import com.intellij.util.xml.DomService;
import com.intellij.util.xml.DomUtil;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.dom.Extension;
import org.jetbrains.idea.devkit.dom.ExtensionPoint;
import org.jetbrains.idea.devkit.dom.IdeaPlugin;
import org.jetbrains.idea.devkit.inspections.quickfix.PluginDescriptorChooser;
import org.jetbrains.idea.devkit.util.PsiUtil;

import java.util.List;

public class InspectionDescriptionInfo {

  private final String myFilename;
  private final PsiMethod myMethod;
  private final PsiFile myDescriptionFile;

  private InspectionDescriptionInfo(String filename, @Nullable PsiMethod method, @Nullable PsiFile descriptionFile) {
    myFilename = filename;
    myMethod = method;
    myDescriptionFile = descriptionFile;
  }

  public static InspectionDescriptionInfo create(Module module, PsiClass psiClass) {
    PsiMethod method = PsiUtil.findNearestMethod("getShortName", psiClass);
    if (method != null &&
        DescriptionType.INSPECTION.getClassName().equals(method.getContainingClass().getQualifiedName())) {
      method = null;
    }
    String filename = null;
    if (method == null) {
      String className = psiClass.getQualifiedName();
      if(className != null) {
        Extension extension = findExtension(module, psiClass);
        if(extension != null) {
          filename = extension.getXmlTag().getAttributeValue("shortName");
        }
      }
      if(filename == null) {
        filename = InspectionProfileEntry.getShortName(psiClass.getName());
      }
    }
    else {
      filename = PsiUtil.getReturnedLiteral(method, psiClass);
    }

    PsiFile descriptionFile = resolveInspectionDescriptionFile(module, filename);
    return new InspectionDescriptionInfo(filename, method, descriptionFile);
  }

  @Nullable
  static Extension findExtension(Module module, PsiClass psiClass) {
    List<DomFileElement<IdeaPlugin>> elements = DomService.getInstance().getFileElements(IdeaPlugin.class, module.getProject(),
                                                                                         GlobalSearchScope.projectScope(module.getProject()));
    elements = ContainerUtil.filter(elements, element -> {
      VirtualFile virtualFile = element.getFile().getVirtualFile();
      return virtualFile != null && ProjectRootManager.getInstance(module.getProject()).getFileIndex().isInContent(virtualFile);
    });

    elements = PluginDescriptorChooser.findAppropriateIntelliJModule(module.getName(), elements);

    Query<PsiReference> query =
      ReferencesSearch.search(psiClass, new LocalSearchScope(elements.stream().map(DomFileElement::getFile).toArray(PsiElement[]::new)));

    for(PsiReference ref : query) {
      PsiElement element = ref.getElement();
      if(element instanceof XmlAttributeValue) {
        PsiElement parent = element.getParent();
        if(parent instanceof XmlAttribute && "implementationClass".equals(((XmlAttribute)parent).getName())) {
          DomElement domElement = DomUtil.getDomElement(parent.getParent());
          if(domElement instanceof Extension) {
            Extension extension = (Extension)domElement;
            ExtensionPoint extensionPoint = extension.getExtensionPoint();
            if(extensionPoint != null &&
               InheritanceUtil.isInheritor(extensionPoint.getBeanClass().getValue(), InspectionEP.class.getName())) {
              return extension;
            }
          }
        }
      }
    }
    return null;
  }

  @Nullable
  private static PsiFile resolveInspectionDescriptionFile(Module module, @Nullable String filename) {
    if (filename == null) return null;

    for (PsiDirectory description : DescriptionCheckerUtil.getDescriptionsDirs(module, DescriptionType.INSPECTION)) {
      final PsiFile file = description.findFile(filename + ".html");
      if (file == null) continue;
      final VirtualFile vf = file.getVirtualFile();
      if (vf == null) continue;
      if (vf.getNameWithoutExtension().equals(filename)) {
        return PsiManager.getInstance(module.getProject()).findFile(vf);
      }
    }
    return null;
  }

  public boolean isValid() {
    return myFilename != null;
  }

  public String getFilename() {
    assert isValid();
    return myFilename;
  }

  @Nullable
  public PsiMethod getShortNameMethod() {
    return myMethod;
  }

  @Nullable
  public PsiFile getDescriptionFile() {
    return myDescriptionFile;
  }

  public boolean hasDescriptionFile() {
    return getDescriptionFile() != null;
  }
}
