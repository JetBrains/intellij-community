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
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.ProjectScope;
import com.intellij.psi.search.PsiNonJavaFileReferenceProcessor;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/**
 * @author Dmitry Avdeev
 *         Date: 1/19/12
 */
public class RegisterExtensionFixProvider implements UnusedDeclarationFixProvider {

  @NotNull
  @Override
  public IntentionAction[] getQuickFixes(@NotNull PsiElement element) {
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
    List<ExtensionPointCandidate> candidateList = new ArrayList<ExtensionPointCandidate>();
    findExtensionPointCandidatesInHierarchy(parentClass, candidateList, new HashSet<PsiClass>());
    if (!candidateList.isEmpty()) {
      return new IntentionAction[] { new RegisterExtensionFix(parentClass, candidateList) };
    }
    return IntentionAction.EMPTY_ARRAY;
  }

  private static void findExtensionPointCandidatesInHierarchy(PsiClass aClass,
                                                              List<ExtensionPointCandidate> list,
                                                              HashSet<PsiClass> processed) {
    for (PsiClass superClass : aClass.getSupers()) {
      if (!processed.add(superClass) || CommonClassNames.JAVA_LANG_OBJECT.equals(superClass.getQualifiedName())) {
        continue;
      }
      findExtensionPointCandidates(superClass, list);
      findExtensionPointCandidatesInHierarchy(superClass, list, processed);
    }
  }

  private static void findExtensionPointCandidates(PsiClass aClass, final List<ExtensionPointCandidate> list) {
    String name = aClass.getQualifiedName();
    if (name == null) {
      return;
    }
    GlobalSearchScope scope = GlobalSearchScope.getScopeRestrictedByFileTypes(ProjectScope.getAllScope(aClass.getProject()), XmlFileType.INSTANCE);
    PsiSearchHelper.SERVICE.getInstance(aClass.getProject()).processUsagesInNonJavaFiles(name, new PsiNonJavaFileReferenceProcessor() {
      @Override
      public boolean process(PsiFile file, int startOffset, int endOffset) {
        PsiElement element = file.findElementAt(startOffset);
        processExtensionPointCandidate(element, list);
        return true;
      }
    }, scope);
  }

  private static void processExtensionPointCandidate(PsiElement element, List<ExtensionPointCandidate> list) {
    XmlTag tag = PsiTreeUtil.getParentOfType(element, XmlTag.class);
    if (tag == null) return;
    if ("extensionPoint".equals(tag.getName())) {
      String epName = getEPName(tag);
      if (epName != null) {
        list.add(new ExtensionPointCandidate(epName));
      }
    }
    else if ("with".equals(tag.getName())) {
      XmlTag extensionPointTag = tag.getParentTag();
      if (!"extensionPoint".equals(extensionPointTag.getName())) return;
      String attrName = tag.getAttributeValue("attribute");
      String tagName = tag.getAttributeValue("tag");
      String epName = getEPName(extensionPointTag);
      String beanClassName = extensionPointTag.getAttributeValue("beanClass");
      if ((attrName == null && tagName == null) || epName == null) return;
      list.add(new ExtensionPointCandidate(epName, attrName, tagName, beanClassName));
    }
  }

  private static String getEPName(XmlTag tag) {
    String qName = tag.getAttributeValue("qualifiedName");
    if (qName != null) {
      return qName;
    }
    String name = tag.getAttributeValue("name");
    if (name != null) {
      return "com.intellij." + name;
    }
    return null;
  }
}
