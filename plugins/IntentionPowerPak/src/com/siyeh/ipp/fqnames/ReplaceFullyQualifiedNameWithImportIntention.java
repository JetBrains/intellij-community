/*
 * Copyright 2003-2015 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ipp.fqnames;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.IntentionPowerPackBundle;
import com.siyeh.ig.psiutils.ImportUtils;
import com.siyeh.ig.style.UnnecessaryFullyQualifiedNameInspection;
import com.siyeh.ipp.base.Intention;
import com.siyeh.ipp.base.PsiElementPredicate;
import com.siyeh.ipp.psiutils.HighlightUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * @see com.siyeh.ig.style.UnnecessaryFullyQualifiedNameInspection
 */
public class ReplaceFullyQualifiedNameWithImportIntention extends Intention {

  @Override
  @NotNull
  public PsiElementPredicate getElementPredicate() {
    return new FullyQualifiedNamePredicate();
  }

  @Override
  public void processIntention(@NotNull PsiElement element) {
    PsiJavaCodeReferenceElement reference = (PsiJavaCodeReferenceElement)element;
    PsiElement target = reference.resolve();
    if (!(target instanceof PsiClass)) {
      PsiElement parent = reference.getParent();
      while (parent instanceof PsiJavaCodeReferenceElement) {
        reference = (PsiJavaCodeReferenceElement)parent;
        target = reference.resolve();
        if (target instanceof PsiClass) {
          break;
        }
        parent = parent.getParent();
      }
    }
    if (!(target instanceof PsiClass)) {
      return;
    }
    final PsiClass aClass = (PsiClass)target;
    final String qualifiedName = aClass.getQualifiedName();
    if (qualifiedName == null) {
      return;
    }
    final PsiJavaFile file =
      PsiTreeUtil.getParentOfType(reference, PsiJavaFile.class);
    if (file == null) {
      return;
    }
    ImportUtils.addImportIfNeeded(aClass, reference);
    final String fullyQualifiedText = reference.getText();
    final UnnecessaryFullyQualifiedNameInspection.QualificationRemover qualificationRemover =
      new UnnecessaryFullyQualifiedNameInspection.QualificationRemover(fullyQualifiedText);
    file.accept(qualificationRemover);
    final Collection<PsiElement> shortenedElements = qualificationRemover.getShortenedElements();
    final int elementCount = shortenedElements.size();
    final String text;
    if (elementCount == 1) {
      text = IntentionPowerPackBundle.message(
        "1.fully.qualified.name.status.bar.escape.highlighting.message");
    }
    else {
      text = IntentionPowerPackBundle.message(
        "multiple.fully.qualified.names.status.bar.escape.highlighting.message",
        Integer.valueOf(elementCount));
    }
    HighlightUtil.highlightElements(shortenedElements, text);
  }
}