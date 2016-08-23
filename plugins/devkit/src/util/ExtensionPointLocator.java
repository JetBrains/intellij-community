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
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiNonJavaFileReferenceProcessor;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlToken;
import com.intellij.util.SmartList;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomService;
import com.intellij.util.xml.DomUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.dom.Extension;
import org.jetbrains.idea.devkit.dom.ExtensionPoint;
import org.jetbrains.idea.devkit.dom.IdeaPlugin;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;

public class ExtensionPointLocator {

  private final PsiClass myPsiClass;

  public ExtensionPointLocator(PsiClass psiClass) {
    myPsiClass = psiClass;
  }

  public List<ExtensionPointCandidate> findDirectCandidates() {
    final List<ExtensionPointCandidate> candidates = new SmartList<>();
    findExtensionPointCandidates(myPsiClass, candidates);
    return candidates;
  }

  public List<ExtensionPointCandidate> findSuperCandidates() {
    final List<ExtensionPointCandidate> candidates = new SmartList<>();
    findExtensionPointCandidatesInHierarchy(myPsiClass, candidates, new HashSet<>());
    return candidates;
  }

  private static void findExtensionPointCandidatesInHierarchy(PsiClass psiClass,
                                                              List<ExtensionPointCandidate> list,
                                                              HashSet<PsiClass> processed) {
    for (PsiClass superClass : psiClass.getSupers()) {
      if (!processed.add(superClass) ||
          CommonClassNames.JAVA_LANG_OBJECT.equals(superClass.getQualifiedName())) {
        continue;
      }
      findExtensionPointCandidates(superClass, list);
      findExtensionPointCandidatesInHierarchy(superClass, list, processed);
    }
  }

  private static void findExtensionPointCandidates(PsiClass psiClass, final List<ExtensionPointCandidate> list) {
    String name = psiClass.getQualifiedName();
    if (name == null) return;

    Project project = psiClass.getProject();
    GlobalSearchScope scope = getCandidatesScope(project);
    PsiSearchHelper.SERVICE.getInstance(project).processUsagesInNonJavaFiles(name, new PsiNonJavaFileReferenceProcessor() {
      @Override
      public boolean process(PsiFile file, int startOffset, int endOffset) {
        PsiElement element = file.findElementAt(startOffset);
        processExtensionPointCandidate(element, list);
        return true;
      }
    }, scope);
  }

  @NotNull
  private static GlobalSearchScope getCandidatesScope(@NotNull Project project) {
    Collection<VirtualFile> candidates = DomService.getInstance().getDomFileCandidates(IdeaPlugin.class, project, GlobalSearchScope.allScope(project));
    return GlobalSearchScope.filesScope(project, candidates);
  }

  public static boolean isRegisteredExtension(@NotNull PsiClass psiClass) {
    String name = psiClass.getQualifiedName();
    if (name == null) return false;

    Project project = psiClass.getProject();
    GlobalSearchScope scope = getCandidatesScope(project);
    return !PsiSearchHelper.SERVICE.getInstance(project).processUsagesInNonJavaFiles(name, new PsiNonJavaFileReferenceProcessor() {
      @Override
      public boolean process(PsiFile file, int startOffset, int endOffset) {
        PsiElement at = file.findElementAt(startOffset);
        String tokenText = at instanceof XmlToken ? at.getText() : null;
        if (!StringUtil.equals(name, tokenText)) return true;
        XmlTag tag = PsiTreeUtil.getParentOfType(at, XmlTag.class);
        if (tag == null) return true;
        DomElement dom = DomUtil.getDomElement(tag);
        return !(dom instanceof Extension && ((Extension)dom).getExtensionPoint() != null);
      }
    }, scope);
  }

  private static void processExtensionPointCandidate(PsiElement element, List<ExtensionPointCandidate> list) {
    XmlTag tag = PsiTreeUtil.getParentOfType(element, XmlTag.class);
    if (tag == null) return;
    if ("extensionPoint".equals(tag.getName())) {
      String epName = getEPName(tag);
      if (epName != null) {
        list.add(new ExtensionPointCandidate(createPointer(tag), epName));
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
      list.add(new ExtensionPointCandidate(createPointer(extensionPointTag), epName, attrName, tagName, beanClassName));
    }
  }

  private static SmartPsiElementPointer createPointer(XmlTag extensionPointTag) {
    return SmartPointerManager.getInstance(extensionPointTag.getProject()).createSmartPsiElementPointer(extensionPointTag);
  }

  @Nullable
  private static String getEPName(XmlTag tag) {
    final DomElement domElement = DomUtil.getDomElement(tag);
    if (!(domElement instanceof ExtensionPoint)) return null;
    return ((ExtensionPoint)domElement).getEffectiveQualifiedName();
  }
}
