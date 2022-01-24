// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring;

import com.intellij.lang.ContextAwareActionHandler;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

/**
 * RefactoringActionHandler is an implementation of a specific IDE refactoring,
 * with dialogs, UI and all.
 * It is what gets invoked when user chooses an item from 'Refactoring' menu.<br>
 * <p>
 * {@code RefactoringActionHandler} is a &quot;one-shot&quot; object: you should not
 * invoke it twice.
 * <p>
 * Use {@link ContextAwareActionHandler} to hide an action from popups but allow access by shortcut, main menu or find.
 *
 * @see RefactoringActionHandlerFactory
 */
public interface RefactoringActionHandler {
  /**
   * Invokes refactoring action from editor. The refactoring obtains
   * all data from editor selection.
   *
   * @param project     the project in which the refactoring is invoked.
   * @param editor      editor that refactoring is invoked in
   * @param file        file should correspond to {@code editor}
   * @param dataContext can be {@code null} for some but not all of refactoring action handlers
   *                    (it is recommended to pass {@code DataManager.getDataContext()} instead of {@code null})
   */
  void invoke(@NotNull Project project, Editor editor, PsiFile file, DataContext dataContext);

  /**
   * Invokes refactoring action from elsewhere (not from editor). Some refactorings
   * do not implement this method.
   *
   * @param project     the project in which the refactoring is invoked.
   * @param elements    list of elements that refactoring should work on. Refactoring-dependent.
   * @param dataContext can be {@code null} for some but not all of refactoring action handlers
   *                    (it is recommended to pass {@code DataManager.getDataContext()} instead of {@code null})
   */
  void invoke(@NotNull Project project, PsiElement @NotNull [] elements, DataContext dataContext);
}