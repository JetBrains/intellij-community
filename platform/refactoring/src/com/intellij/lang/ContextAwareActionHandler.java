// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.lang;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

public interface ContextAwareActionHandler {
  /**
   * Handlers could provide useful hints when they are actually not available, e.g.
   * 'No methods to implement', 'Selected block should represent ...', 'Caret should be positioned at the name of ...', etc.
   * At the same time, when the action is invoked through refactorings quick list popup,
   * generate popup or in another manner but not through main menu (shortcut or find action are treated the same),
   * it's better to hide the action: it would pollute menu with one more choice but can't do anything.
   *
   * @return It's assumed that handler is valid for file. Still should be lightweight, because is invoked from action update.
   *         false - if action won't proceed
   */
  boolean isAvailableForQuickList(@NotNull Editor editor, @NotNull PsiFile file, @NotNull DataContext dataContext);
}
