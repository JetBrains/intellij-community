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
package org.jetbrains.idea.devkit.inspections.quickfix;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.GlobalInspectionTool;
import com.intellij.codeInspection.InspectionEP;
import com.intellij.codeInspection.LocalInspectionEP;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.reference.UnusedDeclarationFixProvider;
import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.ProjectScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;

/**
 * @author Dmitry Avdeev
 *         Date: 1/19/12
 */
public class RegisterExtensionFixProvider implements UnusedDeclarationFixProvider {

  @NotNull
  @Override
  public IntentionAction[] getQuickFixes(PsiElement element) {
    if (!(element instanceof PsiIdentifier)) return IntentionAction.EMPTY_ARRAY;
    PsiElement parent = element.getParent();
    if (!(parent instanceof PsiClass)) return IntentionAction.EMPTY_ARRAY;
    PsiClass parentClass = (PsiClass)parent;
    if (InheritanceUtil.isInheritor(parentClass, LocalInspectionTool.class.getName())) {
      return new IntentionAction[] { new RegisterInspectionFix(parentClass, LocalInspectionEP.LOCAL_INSPECTION) };
    }
    if (InheritanceUtil.isInheritor(parentClass, GlobalInspectionTool.class.getName())) {
      return new IntentionAction[] { new RegisterInspectionFix(parentClass, InspectionEP.GLOBAL_INSPECTION) };
    }
    PsiField epField = findEPNameField(parentClass);
    if (epField != null) {
      String epName = findEPNameForClass(epField.getContainingClass());
      if (epName != null) {
        return new IntentionAction[] { new RegisterExtensionFix(parentClass, epName) };
      }
    }
    return IntentionAction.EMPTY_ARRAY;
  }

  private static String findEPNameForClass(PsiClass aClass) {
    GlobalSearchScope scope = GlobalSearchScope.getScopeRestrictedByFileTypes(ProjectScope.getAllScope(aClass.getProject()), XmlFileType.INSTANCE);
    for (PsiReference reference : ReferencesSearch.search(aClass, scope)) {
      XmlTag tag = PsiTreeUtil.getParentOfType(reference.getElement(), XmlTag.class);
      if (tag != null && "extensionPoint".equals(tag.getName())) {
        String qName = tag.getAttributeValue("qualifiedName");
        if (qName != null) {
          return qName;
        }
        String name = tag.getAttributeValue("name");
        if (name != null) {
          return "com.intellij." + name;
        }
      }
    }
    return null;
  }

  private static PsiField findEPNameField(PsiClass aClass) {
    for (PsiField field : aClass.getFields()) {
      if (field.getType() instanceof PsiClassType) {
        PsiClassType classType = (PsiClassType)field.getType();
        PsiClassType.ClassResolveResult resolved = classType.resolveGenerics();
        PsiClass fieldClass = resolved.getElement();
        if (fieldClass != null && ExtensionPointName.class.getName().equals(fieldClass.getQualifiedName())) {
          return field;
        }
      }
    }
    for (PsiClass superClass: aClass.getSupers()) {
      PsiField epField = findEPNameField(superClass);
      if (epField != null) {
        return epField;
      }
    }
    return null;
  }
}
