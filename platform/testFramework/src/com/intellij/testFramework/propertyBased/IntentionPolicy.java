// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.propertyBased;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.IntentionActionDelegate;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ex.QuickFixWrapper;
import com.intellij.modcommand.ModCommand;
import com.intellij.modcommand.ModDisplayMessage;
import com.intellij.modcommand.ModUpdateSystemOptions;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

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
    if ((!action.startInWriteAction() && action.asModCommandAction() == null) || shouldSkipIntention(action.getText())) {
      return false;
    }
    IntentionAction original = IntentionActionDelegate.unwrap(action);
    String familyName;
    LocalQuickFix fix = QuickFixWrapper.unwrap(original);
    if (fix != null) {
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
  public @NotNull List<PsiElement> getElementsToWrap(@NotNull PsiElement currentElement) {
    return Collections.emptyList();
  }

  /**
   * @return a wrap prefix for {@link #getElementsToWrap(PsiElement)}.
   */
  public @NotNull String getWrapPrefix() { return "";}

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

  /**
   * @param modCommand command to validate (already unpacked, not composite)
   * @return non-null message if the command should be skipped, null if it's ok to execute such a command
   */
  public @Nullable String validateCommand(@NotNull ModCommand modCommand) {
    // TODO: debug commands that do nothing. This should not be generally the case
    if (modCommand instanceof ModDisplayMessage message && message.kind() == ModDisplayMessage.MessageKind.ERROR) {
      return "Error: " + message.messageText();
    }
    if (modCommand instanceof ModUpdateSystemOptions option) {
      return "Updates "+option.options().stream().map(opt -> opt.bindId()).collect(Collectors.joining("; "));
    }
    return null;
  }
}
