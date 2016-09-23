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
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.util.Query;
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

import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
    return CachedValuesManager.getCachedValue(psiClass, () -> {
      Extension extension = doFindExtension(module, psiClass);
      return CachedValueProvider.Result
        .create(extension, extension == null ? PsiModificationTracker.MODIFICATION_COUNT : extension.getXmlTag());
    });
  }

  @Nullable
  private static Extension doFindExtension(Module module, PsiClass psiClass) {
    // Try search in narrow scopes first
    Project project = module.getProject();
    Set<DomFileElement<IdeaPlugin>> processed = new HashSet<>();
    for(GlobalSearchScope scope : DescriptionCheckerUtil.searchScopes(module)) {
      List<DomFileElement<IdeaPlugin>> origElements = DomService.getInstance().getFileElements(IdeaPlugin.class, project, scope);
      origElements.removeAll(processed);
      List<DomFileElement<IdeaPlugin>> elements = PluginDescriptorChooser.findAppropriateIntelliJModule(module.getName(), origElements);

      Query<PsiReference> query =
        ReferencesSearch.search(psiClass, new LocalSearchScope(elements.stream().map(DomFileElement::getFile).toArray(PsiElement[]::new)));

      Ref<Extension> result = Ref.create(null);
      query.forEach(ref -> {
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
                result.set(extension);
                return false;
              }
            }
          }
        }
        return true;
      });
      Extension extension = result.get();
      if(extension != null) return extension;
      processed.addAll(origElements);
    }
    return null;
  }

  @Nullable
  private static PsiFile resolveInspectionDescriptionFile(Module module, @Nullable String filename) {
    if (filename == null) return null;

    String nameWithSuffix = filename + ".html";
    return DescriptionCheckerUtil.allDescriptionDirs(module, DescriptionType.INSPECTION)
      .map(description -> description.findFile(nameWithSuffix)).nonNull().findFirst().orElse(null);
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
