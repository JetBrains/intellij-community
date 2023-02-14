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
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.lang.LanguageWordCompletion;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.paths.WebReference;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.reference.impl.PsiMultiReference;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtilCore;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
  @Nullable
  protected String getExpectedVariant(@NotNull Editor editor, @NotNull PsiFile file, @NotNull PsiElement leaf, @Nullable PsiReference ref) {
    if (isAfterError(file, leaf)) {
      return null;
    }
    
    String leafText = ref != null ? ref.getRangeInElement().substring(ref.getElement().getText()) : leaf.getText();
    if (leafText.isEmpty() ||
        !Character.isLetter(leafText.charAt(0)) ||
        leaf instanceof PsiWhiteSpace ||
        PsiTreeUtil.getNonStrictParentOfType(leaf, PsiComment.class) != null) {
      return null;
    }

    IElementType leafType = PsiUtilCore.getElementType(leaf);
    if ("org.intellij.lang.regexp.RegExpElementType".equals(leafType.getClass().getName())) {
      if (leafText.length() == 1) {
        return null; // regexp has a token for each character: not interesting (and no completion)
      }
      if ("NAME".equals(leafType.toString())) {
        return null; // group name, no completion expected
      }
    }

    if (isDeclarationName(editor, file, leaf, ref)) return null;

    if (ref != null) {
      PsiElement target = getValidResolveResult(ref);
      if (target == null || !shouldSuggestReferenceText(ref, target)) return null;
      
      if (ref instanceof PsiMultiReference) {
        for (PsiReference ref1 : ((PsiMultiReference)ref).getReferences()) {
          if (target.getClass().isInstance(ref1.resolve()) && !shouldSuggestReferenceText(ref1, target)) return null;
        }
      }
    }
    else {
      if (!SyntaxTraverser.psiTraverser(file).filter(PsiErrorElement.class).isEmpty()) {
        return null;
      }
      if (LanguageWordCompletion.INSTANCE.isEnabledIn(leafType)) {
        // Looks like plain text. And the word under caret is excluded from word completion anyway.
        return null;
      }
      if (!shouldSuggestNonReferenceLeafText(leaf)) return null;
    }
    return leafText;
  }

  protected boolean isAfterError(@NotNull PsiFile file, @NotNull PsiElement leaf) {
    return MadTestingUtil.isAfterError(file, leaf.getTextRange().getStartOffset());
  }

  public boolean shouldCheckDuplicates(@NotNull Editor editor, @NotNull PsiFile file, @Nullable PsiElement leaf) {
    return leaf != null && !isAfterError(file, leaf);
  }

  /**
   * @return whether it's OK for two lookup elements at the same place to have the same presentation (e.g. due to errors in the source code)
   */
  public boolean areDuplicatesOk(@NotNull LookupElement item1, @NotNull LookupElement item2) {
    return false;
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

  private static boolean isDeclarationName(Editor editor, PsiFile file, PsiElement leaf, @Nullable PsiReference ref) {
    PsiElement target = TargetElementUtil.findTargetElement(editor, TargetElementUtil.ELEMENT_NAME_ACCEPTED | TargetElementUtil.REFERENCED_ELEMENT_ACCEPTED);
    if (target != null) target = target.getNavigationElement();
    PsiFile targetFile = target != null ? target.getContainingFile() : null;
    if (targetFile == null || targetFile.getViewProvider() != file.getViewProvider()) {
      return false;
    }
    if (target.getTextOffset() == leaf.getTextRange().getStartOffset() ||
        ref != null && target.getTextOffset() == ref.getElement().getTextRange().getStartOffset() + ref.getRangeInElement().getStartOffset()) {
      return true;
    }
    if (target instanceof PsiNameIdentifierOwner) {
      PsiElement nameIdentifier = ((PsiNameIdentifierOwner)target).getNameIdentifier();
      if (nameIdentifier != null && PsiTreeUtil.isAncestor(nameIdentifier, leaf, false)) return true;
    }
    return false;
  }

  protected boolean shouldSuggestNonReferenceLeafText(@NotNull PsiElement leaf) {
    return true;
  }

  protected boolean shouldSuggestReferenceText(@NotNull PsiReference ref, @NotNull PsiElement target) { 
    return !(ref instanceof WebReference);
  }
}
