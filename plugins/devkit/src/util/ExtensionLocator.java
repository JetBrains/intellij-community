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
package org.jetbrains.idea.devkit.util;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlToken;
import com.intellij.util.SmartList;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.dom.Extension;

import java.util.List;

public class ExtensionLocator extends LocatorBase {
  private final PsiClass myPsiClass;

  public ExtensionLocator(PsiClass aClass) {
    myPsiClass = aClass;
  }

  public List<ExtensionCandidate> findDirectCandidates() {
    final List<ExtensionCandidate> candidates = new SmartList<>();
    findExtensionCandidates(myPsiClass, candidates);
    return candidates;
  }

  private static void findExtensionCandidates(PsiClass psiClass, final List<ExtensionCandidate> list) {
    String name = psiClass.getQualifiedName();
    if (name == null) return;

    Project project = psiClass.getProject();
    GlobalSearchScope scope = getCandidatesScope(project);

    //TODO duplicate with ExtensionPointLocator.isRegisteredExtension()  +  don't do it twice
    PsiSearchHelper.SERVICE.getInstance(project).processUsagesInNonJavaFiles(name, (file, startOffset, endOffset) -> {
      PsiElement element = file.findElementAt(startOffset);
      String tokenText = element instanceof XmlToken ? element.getText() : null;
      if (!StringUtil.equals(name, tokenText)) return true;
      XmlTag tag = PsiTreeUtil.getParentOfType(element, XmlTag.class);
      if (tag == null) return true;
      DomElement dom = DomUtil.getDomElement(tag);
      if (dom instanceof Extension && ((Extension)dom).getExtensionPoint() != null) {
        list.add(new ExtensionCandidate(createPointer(tag)));
        return false;
      }
      return true;
    }, scope);
  }

  public static boolean isRegisteredExtension(@NotNull PsiClass psiClass) {
    String name = psiClass.getQualifiedName();
    if (name == null) return false;

    Project project = psiClass.getProject();
    GlobalSearchScope scope = getCandidatesScope(project);
    return !PsiSearchHelper.SERVICE.getInstance(project).processUsagesInNonJavaFiles(name, (file, startOffset, endOffset) -> {
      PsiElement at = file.findElementAt(startOffset);
      String tokenText = at instanceof XmlToken ? at.getText() : null;
      if (!StringUtil.equals(name, tokenText)) return true;
      XmlTag tag = PsiTreeUtil.getParentOfType(at, XmlTag.class);
      if (tag == null) return true;
      DomElement dom = DomUtil.getDomElement(tag);
      return !(dom instanceof Extension && ((Extension)dom).getExtensionPoint() != null);
    }, scope);
  }
}
