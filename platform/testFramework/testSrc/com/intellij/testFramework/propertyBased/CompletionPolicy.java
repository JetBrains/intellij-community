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
package com.intellij.testFramework.propertyBased;

import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author peter
 */
public class CompletionPolicy {

  /**
   * @return string consisting of characters that can be used to insert the chosen lookup item
   */
  public String getPossibleSelectionCharacters() {
    return "\n\t\r .(";
  }

  /**
   * @return the lookup string of an element that should be suggested in the given position
   */
  public String getExpectedVariant(Editor editor, PsiFile file) {
    PsiElement leaf = file.findElementAt(editor.getCaretModel().getOffset());
    if (leaf == null) {
      return null;
    }
    PsiReference ref = file.findReferenceAt(editor.getCaretModel().getOffset());
    return getExpectedVariant(editor, file, leaf, ref);

  }

  @Nullable
  protected String getExpectedVariant(@NotNull Editor editor, @NotNull PsiFile file, @NotNull PsiElement leaf, @Nullable PsiReference ref) {
    if (MadTestingUtil.isAfterError(file, leaf.getTextRange().getStartOffset())) {
      return null;
    }
    
    String leafText = leaf.getText();
    if (leafText.isEmpty() ||
        !Character.isLetter(leafText.charAt(0)) ||
        leaf instanceof PsiWhiteSpace ||
        PsiTreeUtil.getNonStrictParentOfType(leaf, PsiComment.class) != null) {
      return null;
    }

    if (isDeclarationName(editor, file, leaf)) return null;

    if (ref != null) {
      PsiElement target = getValidResolveResult(ref);
      if (target == null || !shouldSuggestReferenceText(ref, target)) return null;
    }
    else {
      if (!SyntaxTraverser.psiTraverser(file).filter(PsiErrorElement.class).isEmpty()) {
        return null;
      }
      if (!shouldSuggestNonReferenceLeafText(leaf)) return null;
    }
    return leafText;
  }

  private static PsiElement getValidResolveResult(@NotNull PsiReference ref) {
    if (ref instanceof PsiPolyVariantReference) {
      for (ResolveResult result : ((PsiPolyVariantReference)ref).multiResolve(false)) {
        if (!result.isValidResult()) {
          return null;
        }
      }
    }
    return ref.resolve();
  }

  private static boolean isDeclarationName(Editor editor, PsiFile file, PsiElement leaf) {
    PsiElement target = TargetElementUtil.findTargetElement(editor, TargetElementUtil.ELEMENT_NAME_ACCEPTED | TargetElementUtil.REFERENCED_ELEMENT_ACCEPTED);
    if (target != null) target = target.getNavigationElement();
    PsiFile targetFile = target != null ? target.getContainingFile() : null;
    return targetFile != null && targetFile.getViewProvider() == file.getViewProvider() && 
           target.getTextOffset() == leaf.getTextRange().getStartOffset();
  }

  protected boolean shouldSuggestNonReferenceLeafText(@NotNull PsiElement leaf) {
    return true;
  }

  protected boolean shouldSuggestReferenceText(@NotNull PsiReference ref, @NotNull PsiElement target) { 
    return true;
  }
}
