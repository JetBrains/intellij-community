// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
  @DetailedDescription private final String myReason;

  public InvestigateFix(@DetailedDescription String reason) {
    myReason = reason;
  }

  static void analyzeStackTrace(Project project, String exceptionText) {
    final UnscrambleDialog dialog = new UnscrambleDialog(project);
    dialog.setText(exceptionText);
    dialog.show();
  }

  @NotNull
  @Override
  public String getText() {
    return GroovyBundle.message("investigate.gdsl.error.intention.name");
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return GroovyBundle.message("investigate.gdsl.error.family.name");
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    return true;
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    analyzeStackTrace(project, myReason);
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }
}
