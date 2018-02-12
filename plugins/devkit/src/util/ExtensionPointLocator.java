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
package org.jetbrains.idea.devkit.util;

import com.intellij.openapi.project.Project;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.util.ClassUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.containers.SmartHashSet;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomUtil;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.dom.ExtensionPoint;

import java.util.HashSet;
import java.util.Set;

public class ExtensionPointLocator {
  private final PsiClass myPsiClass;

  public ExtensionPointLocator(PsiClass psiClass) {
    myPsiClass = psiClass;
  }


  public Set<ExtensionPointCandidate> findDirectCandidates() {
    Set<ExtensionPointCandidate> candidates = new SmartHashSet<>();
    findExtensionPointCandidates(myPsiClass, candidates);
    return candidates;
  }

  public Set<ExtensionPointCandidate> findSuperCandidates() {
    Set<ExtensionPointCandidate> candidates = new SmartHashSet<>();
    findExtensionPointCandidatesInHierarchy(myPsiClass, candidates, new HashSet<>());
    return candidates;
  }

  private static void findExtensionPointCandidatesInHierarchy(PsiClass psiClass,
                                                              Set<ExtensionPointCandidate> candidates,
                                                              HashSet<PsiClass> processed) {
    for (PsiClass superClass : psiClass.getSupers()) {
      if (!processed.add(superClass) || CommonClassNames.JAVA_LANG_OBJECT.equals(superClass.getQualifiedName())) {
        continue;
      }
      findExtensionPointCandidates(superClass, candidates);
      findExtensionPointCandidatesInHierarchy(superClass, candidates, processed);
    }
  }

  private static void findExtensionPointCandidates(PsiClass psiClass, Set<ExtensionPointCandidate> candidates) {
    String name = ClassUtil.getJVMClassName(psiClass);
    if (name == null) return;

    Project project = psiClass.getProject();
    GlobalSearchScope scope = PluginRelatedLocatorsUtils.getCandidatesScope(project);
    PsiSearchHelper.SERVICE.getInstance(project).processUsagesInNonJavaFiles(name, (file, startOffset, endOffset) -> {
      PsiElement element = file.findElementAt(startOffset);
      processExtensionPointCandidate(element, candidates);
      return true;
    }, scope);
  }

  private static void processExtensionPointCandidate(PsiElement element, Set<ExtensionPointCandidate> candidates) {
    XmlTag tag = PsiTreeUtil.getParentOfType(element, XmlTag.class);
    if (tag == null) return;
    if ("extensionPoint".equals(tag.getName())) {
      String epName = getEPName(tag);
      if (epName != null) {
        candidates.add(new ExtensionPointCandidate(SmartPointerManager.getInstance(tag.getProject()).createSmartPsiElementPointer(tag), epName));
      }
    }
    else if ("with".equals(tag.getName())) {
      XmlTag extensionPointTag = tag.getParentTag();
      if (extensionPointTag == null) return;
      if (!"extensionPoint".equals(extensionPointTag.getName())) return;
      String attrName = tag.getAttributeValue("attribute");
      String tagName = tag.getAttributeValue("tag");
      String epName = getEPName(extensionPointTag);
      String beanClassName = extensionPointTag.getAttributeValue("beanClass");
      if ((attrName == null && tagName == null) || epName == null) return;
      candidates.add(new ExtensionPointCandidate(SmartPointerManager.getInstance(extensionPointTag.getProject())
                                             .createSmartPsiElementPointer(extensionPointTag), epName, attrName, tagName, beanClassName));
    }
  }

  @Nullable
  private static String getEPName(XmlTag tag) {
    final DomElement domElement = DomUtil.getDomElement(tag);
    if (!(domElement instanceof ExtensionPoint)) return null;
    return ((ExtensionPoint)domElement).getEffectiveQualifiedName();
  }
}
