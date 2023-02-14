// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.lang.refactoring;

import com.intellij.lang.Language;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsActions.ActionText;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.Nullable;

/**
 * An extension point for "Inline" refactorings.
 * <p/>
 * Handlers are processed one by one, the first handler which agrees to handle element ({@link #isEnabledOnElement(PsiElement)}) will be used, rest are ignored.
 * Natural loading order can be changed by providing attribute "order" during registration in plugin.xml.
 */
public abstract class InlineActionHandler {
  public static final ExtensionPointName<InlineActionHandler> EP_NAME = ExtensionPointName.create("com.intellij.inlineActionHandler");

  /**
   * Fast check to see if the handler can possibly inline the element. Called from action update.
   *
   * @param element the element under caret
   * @return true if the handler can possibly inline the element (with some additional conditions), false otherwise.
   */
  public boolean isEnabledOnElement(PsiElement element) {
    return canInlineElement(element);
  }

   /**
   * Fast check to see if the handler can possibly inline the element. Called from action update.
   *
   * @param element the element under caret
   * @param editor editor where refactoring was started or null if e.g., inline was started from project view
   * @return true if the handler can possibly inline the element (with some additional conditions), false otherwise.
   */
  public boolean isEnabledOnElement(PsiElement element, @Nullable Editor editor) {
    return isEnabledOnElement(element);
  }

  /**
   * Fast check to see if handler can possibly inline an element of language {@code l}. 
   */
  public abstract boolean isEnabledForLanguage(Language l);

  /**
   * Can be called from action update if {@link #isEnabledOnElement(PsiElement)} is not overridden.
   * Is called from {@link com.intellij.refactoring.inline.InlineRefactoringActionHandler#invoke(Project, PsiElement[], com.intellij.openapi.actionSystem.DataContext)} to find handler to process {@code element}.
   * @return {@code true} if handler can inline {@code element}.  
   */
  public abstract boolean canInlineElement(PsiElement element);

  /**
   * Can be called from action update if {@link #isEnabledOnElement(PsiElement)} is not overridden.
   * Is called from {@link com.intellij.refactoring.inline.InlineRefactoringActionHandler#invoke(Project, Editor, com.intellij.psi.PsiFile, com.intellij.openapi.actionSystem.DataContext)} to find handler to process {@code element}.
   * @return {@code true} if handler can inline {@code element}.  
   */
  public boolean canInlineElementInEditor(PsiElement element, Editor editor) {
    return canInlineElement(element);
  }

  /**
   * Performs inline refactoring. May show UI to ask user what references to inline or notify that inline is not possible.
   * 
   * @param project the project where inline is performed.
   * @param editor  the editor where refactoring is started.
   * @param element the element to be inlined.
    */
  public abstract void inlineElement(Project project, Editor editor, PsiElement element);

  @Nullable
  @ActionText
  public String getActionName(PsiElement element) {
    return null;
  }
}
