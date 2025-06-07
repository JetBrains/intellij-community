// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.dsl;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts.DetailedDescription;
import com.intellij.psi.PsiFile;
import com.intellij.unscramble.UnscrambleDialog;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyBundle;

public class InvestigateFix implements IntentionAction {
  private final @DetailedDescription String myReason;

  public InvestigateFix(@DetailedDescription String reason) {
    myReason = reason;
  }

  static void analyzeStackTrace(Project project, String exceptionText) {
    final UnscrambleDialog dialog = new UnscrambleDialog(project);
    dialog.setText(exceptionText);
    dialog.show();
  }

  @Override
  public @NotNull String getText() {
    return GroovyBundle.message("investigate.gdsl.error.intention.name");
  }

  @Override
  public @NotNull String getFamilyName() {
    return GroovyBundle.message("investigate.gdsl.error.family.name");
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile psiFile) {
    return true;
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile psiFile) throws IncorrectOperationException {
    analyzeStackTrace(project, myReason);
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }
}
