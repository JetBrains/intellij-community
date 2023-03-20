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

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.IntentionActionDelegate;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ex.QuickFixWrapper;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

public class IntentionPolicy {

  /**
   * Determines whether the given intention should be invoked in the property tests. Possible reasons for not invoking them:
   * <li>
   *   <ul>Intentions that don't change document by design (e.g. change settings, thus affecting following tests)</ul>
   *   <ul>Intentions that start live template (that also doesn't change document)</ul>
   *   <ul>Intentions that display dialogs/popups (but it'd be better to make them testable as well)</ul>
   *   <ul>Intentions requiring special test environment not provided by the property test</ul>
   *   <ul>Intentions ignored because of not-easy-to-fix bugs in them. Preferably should be filed as a tracker issue.</ul>
   * </li> 
   */
  public boolean mayInvokeIntention(@NotNull IntentionAction action) {
    if (!action.startInWriteAction() || shouldSkipIntention(action.getText())) {
      return false;
    }
    IntentionAction original = IntentionActionDelegate.unwrap(action);
    String familyName;
    if (original instanceof QuickFixWrapper) {
      LocalQuickFix fix = ((QuickFixWrapper)original).getFix();
      familyName = fix.getFamilyName();
    }
    else {
      familyName = original.getFamilyName();
    }
    return !shouldSkipByFamilyName(familyName);
  }

  protected boolean shouldSkipIntention(@NotNull String actionText) {
    return actionText.startsWith("Typo: Change to...") || // doesn't change file text (starts live template);
           actionText.startsWith("Convert to project line separators"); // changes VFS, not document
  }

  protected boolean shouldSkipByFamilyName(@NotNull String familyName) {
    return false;
  }

  /**
   * @param action action to check (already allowed by {@link #mayInvokeIntention(IntentionAction)})
   * @return true if it should be checked that given action can generate a preview.
   * By default preview is not checked for any action, provide custom policy to check it.
   */
  protected boolean shouldCheckPreview(@NotNull IntentionAction action) {
    return false;
  }

  /**
   * Controls whether the given intention (already approved by {@link #mayInvokeIntention}) is allowed to
   * introduce new highlighting errors into the code. It's recommended to return false by default, 
   * and include found intentions one by one (or make them not break the code).  
   */
  public boolean mayBreakCode(@NotNull IntentionAction action, @NotNull Editor editor, @NotNull PsiFile file) {
    return "Flip ','".equals(action.getText()); // just does text operations, doesn't care about correctness
  }

  public boolean checkComments(IntentionAction intention) {
    return false;
  }

  public boolean trackComment(PsiComment comment) {
    return true;
  }

  /**
   * Return list of elements which could be wrapped with {@linkplain #getWrapPrefix() wrap prefix} and
   * {@linkplain #getWrapSuffix()} wrap suffix} without changing the available intentions.
   *
   * @param currentElement an element caret is positioned at
   * @return list of elements which could be wrapped. One of them will be selected and wrapped and it will be checked that no intentions
   * changed. Returns an empty list by default which means that no wrapping should be performed
   */
  @NotNull
  public List<PsiElement> getElementsToWrap(@NotNull PsiElement currentElement) {
    return Collections.emptyList();
  }

  /**
   * @return a wrap prefix for {@link #getElementsToWrap(PsiElement)}.
   */
  @NotNull
  public String getWrapPrefix() { return "";}

  /**
   * @return a wrap suffix for {@link #getElementsToWrap(PsiElement)}.
   */
  public String getWrapSuffix() { return "";}

  /**
   * If after intention invocation new errors appeared, allows to suppress test failing because of that. To be used
   * only in cases when {@link #mayBreakCode(IntentionAction, Editor, PsiFile)} isn't enough (e.g. highlighting infrastructure issues).
   */
  public boolean shouldTolerateIntroducedError(@NotNull HighlightInfo info) {
    return false;
  }
}
