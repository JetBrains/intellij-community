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
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlToken;
import com.intellij.util.CommonProcessors;
import com.intellij.util.Processor;
import com.intellij.util.SmartList;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.dom.Extension;

import java.util.Collections;
import java.util.List;

public class ExtensionLocator {
  private final PsiClass myPsiClass;

  public ExtensionLocator(PsiClass aClass) {
    myPsiClass = aClass;
  }

  @NotNull
  public List<ExtensionCandidate> findCandidates() {
    String name = myPsiClass.getQualifiedName();
    if (name == null) {
      return Collections.emptyList();
    }

    List<ExtensionCandidate> result = new SmartList<>();
    processExtensionDeclarations(myPsiClass, new ReferenceProcessor(name, tag -> {
      result.add(new ExtensionCandidate(SmartPointerManager.getInstance(tag.getProject()).createSmartPsiElementPointer(tag)));
      return true; // continue processing
    }));
    return result;
  }

  @SuppressWarnings("unchecked") // it's fine with Processor.FALSE
  public static boolean isRegisteredExtension(@NotNull PsiClass psiClass) {
    String name = psiClass.getQualifiedName();
    if (name == null) {
      return false;
    }

    CommonProcessors.FindFirstProcessor<XmlTag> processor = new CommonProcessors.FindFirstProcessor<>();
    processExtensionDeclarations(psiClass, new ReferenceProcessor(name, processor));
    return processor.isFound();
  }

  private static void processExtensionDeclarations(PsiClass psiClass, PsiNonJavaFileReferenceProcessor referenceProcessor) {
    String name = psiClass.getQualifiedName();
    if (name == null) return;

    Project project = psiClass.getProject();
    GlobalSearchScope scope = PluginRelatedLocatorsUtils.getCandidatesScope(project);

    PsiSearchHelper.SERVICE.getInstance(project).processUsagesInNonJavaFiles(name, referenceProcessor, scope);
  }

  private static class ReferenceProcessor implements PsiNonJavaFileReferenceProcessor {
    private final String myExtensionClassName;
    private final Processor<XmlTag> myExtensionTagHandler;

    private ReferenceProcessor(String name, Processor<XmlTag> extensionTagHandler) {
      myExtensionClassName = name;
      myExtensionTagHandler = extensionTagHandler;
    }

    @Override
    public boolean process(PsiFile file, int startOffset, int endOffset) {
      PsiElement element = file.findElementAt(startOffset);
      String tokenText = element instanceof XmlToken ? element.getText() : null;
      if (!StringUtil.equals(myExtensionClassName, tokenText)) return true;

      XmlTag tag = PsiTreeUtil.getParentOfType(element, XmlTag.class);
      if (tag == null) return true;

      DomElement dom = DomUtil.getDomElement(tag);
      if (dom instanceof Extension && ((Extension)dom).getExtensionPoint() != null) {
        return myExtensionTagHandler.process(tag);
      }
      return true;
    }
  }
}
