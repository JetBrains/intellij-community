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
package org.jetbrains.plugins.groovy.dgm;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

/**
 * @author Max Medvedev
 */
public class DGMClassReference implements PsiReference {
  private final PsiElement myElement;
  private TextRange myRange;

  public DGMClassReference(PsiElement element, int start, int end) {

    myElement = element;
    myRange = new TextRange(start, end);
  }


  @Override
  public PsiElement getElement() {
    return myElement;
  }

  @Override
  public TextRange getRangeInElement() {
    return myRange;
  }

  @Override
  public PsiElement resolve() {
    Project project = myElement.getProject();
    return JavaPsiFacade.getInstance(project).findClass(myRange.substring(myElement.getText()), myElement.getResolveScope());
  }

  @NotNull
  @Override
  public String getCanonicalText() {
    return myRange.substring(myElement.getText());
  }

  @Override
  public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
    return null;  //To change body of implemented methods use File | Settings | File Templates.
  }

  @Override
  public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
    if (element instanceof PsiClass) {
      String qname = ((PsiClass)element).getQualifiedName();
      if (qname == null) return myElement;
      PsiDocumentManager documentManager = PsiDocumentManager.getInstance(myElement.getProject());
      Document document = documentManager.getDocument(myElement.getContainingFile());
      TextRange range = myRange.shiftRight(myElement.getTextRange().getStartOffset());
      document.replaceString(range.getStartOffset(), range.getEndOffset(), qname);
      documentManager.commitDocument(document);
    }
    return myElement;
  }

  @Override
  public boolean isReferenceTo(PsiElement element) {
    return myElement.getManager().areElementsEquivalent(element, resolve());
  }

  @NotNull
  @Override
  public Object[] getVariants() {
    return EMPTY_ARRAY;
  }

  @Override
  public boolean isSoft() {
    return true;
  }
}
