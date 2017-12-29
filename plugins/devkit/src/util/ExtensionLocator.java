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
import com.intellij.psi.PsiFile;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiNonJavaFileReferenceProcessor;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.util.ClassUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlToken;
import com.intellij.util.SmartList;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomManager;
import com.intellij.util.xml.DomUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.dom.Extension;
import org.jetbrains.idea.devkit.dom.ExtensionPoint;

import java.util.Collections;
import java.util.List;

public abstract class ExtensionLocator {
  @NotNull
  public abstract List<ExtensionCandidate> findCandidates();

  public static ExtensionLocator byPsiClass(PsiClass psiClass) {
    return new ExtensionByPsiClassLocator(psiClass);
  }

  public static ExtensionLocator byExtensionPoint(@NotNull ExtensionPoint extensionPoint) {
    return new ExtensionByExtensionPointLocator(extensionPoint, null);
  }

  public static ExtensionLocator byExtensionPointAndId(@NotNull ExtensionPoint extensionPoint, @NotNull String extensionId) {
    return new ExtensionByExtensionPointLocator(extensionPoint, extensionId);
  }


  private static class ExtensionByPsiClassLocator extends ExtensionLocator {
    private final PsiClass myPsiClass;

    ExtensionByPsiClassLocator(PsiClass psiClass) {
      myPsiClass = psiClass;
    }

    @NotNull
    public List<ExtensionCandidate> findCandidates() {
      String name = ClassUtil.getJVMClassName(myPsiClass);
      if (name == null) {
        return Collections.emptyList();
      }

      List<ExtensionCandidate> result = new SmartList<>();
      processExtensionDeclarations(myPsiClass.getQualifiedName(), myPsiClass.getProject(), (file, startOffset, endOffset) -> {
        XmlTag tag = getXmlTagOfTokenElement(file, startOffset, name, true);
        DomElement dom = DomUtil.getDomElement(tag);
        if (dom instanceof Extension && ((Extension)dom).getExtensionPoint() != null) {
          result.add(new ExtensionCandidate(SmartPointerManager.getInstance(tag.getProject()).createSmartPsiElementPointer(tag)));
        }
        return true; // continue processing
      });

      return result;
    }
  }

  private static class ExtensionByExtensionPointLocator extends ExtensionLocator {
    private final ExtensionPoint myExtensionPoint;
    private final String myExtensionId;

    private ExtensionByExtensionPointLocator(@NotNull ExtensionPoint extensionPoint, @Nullable String extensionId) {
      myExtensionPoint = extensionPoint;
      myExtensionId = extensionId;
    }

    @NotNull
    @Override
    public List<ExtensionCandidate> findCandidates() {
      XmlTag epTag = myExtensionPoint.getXmlTag();
      if (epTag == null) {
        return Collections.emptyList();
      }

      Project project = epTag.getProject();
      DomManager domManager = DomManager.getDomManager(project);
      // We must search for the last part of EP name, because for instance 'com.intellij.console.folding' extension
      // may be declared as <extensions defaultExtensionNs="com"><intellij.console.folding ...
      String epNameToSearch = StringUtil.substringAfterLast(myExtensionPoint.getEffectiveQualifiedName(), ".");
      List<ExtensionCandidate> result = new SmartList<>();
      processExtensionDeclarations(epNameToSearch, project, (file, startOffset, endOffset) -> {
        XmlTag tag = getXmlTagOfTokenElement(file, startOffset, epNameToSearch, false);
        if (tag == null) {
          return true;
        }

        DomElement domElement = domManager.getDomElement(tag);
        if (!(domElement instanceof Extension)) {
          return true;
        }

        Extension extension = (Extension)domElement;
        ExtensionPoint ep = extension.getExtensionPoint();
        if (ep == null) {
          return true;
        }

        if (StringUtil.equals(ep.getEffectiveQualifiedName(), myExtensionPoint.getEffectiveQualifiedName())
            && (myExtensionId == null || myExtensionId.equals(extension.getId().getStringValue()))) {
          result.add(new ExtensionCandidate(SmartPointerManager.getInstance(tag.getProject()).createSmartPsiElementPointer(tag)));
          return myExtensionId == null; // stop after the first found candidate if ID is specified
        }

        return true;
      });

      return result;
    }
  }


  private static void processExtensionDeclarations(String name, Project project, PsiNonJavaFileReferenceProcessor referenceProcessor) {
    if (name == null) return;
    GlobalSearchScope scope = PluginRelatedLocatorsUtils.getCandidatesScope(project);
    PsiSearchHelper.SERVICE.getInstance(project).processUsagesInNonJavaFiles(name, referenceProcessor, scope);
  }

  @Nullable
  private static XmlTag getXmlTagOfTokenElement(PsiFile file, int startOffset, String matchStr, boolean strictMatch) {
    PsiElement element = file.findElementAt(startOffset);
    String tokenText = element instanceof XmlToken ? element.getText() : null;
    if (tokenText == null) {
      return null;
    }
    if (!strictMatch && !StringUtil.contains(tokenText, matchStr)) {
      return null;
    }
    if (strictMatch && !StringUtil.equals(tokenText, matchStr)) {
      return null;
    }
    return PsiTreeUtil.getParentOfType(element, XmlTag.class);
  }
}
