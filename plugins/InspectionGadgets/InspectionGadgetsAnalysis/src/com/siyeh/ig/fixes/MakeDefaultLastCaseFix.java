// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.fixes;

import com.intellij.codeInsight.daemon.impl.actions.IntentionActionWithFixAllOption;
import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement;
import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class MakeDefaultLastCaseFix extends LocalQuickFixAndIntentionActionOnPsiElement implements IntentionActionWithFixAllOption {

  public MakeDefaultLastCaseFix(@NotNull PsiSwitchLabelStatementBase labelStatementBase) {
    super(labelStatementBase);
  }

  @Nls(capitalization = Nls.Capitalization.Sentence)
  @NotNull
  @Override
  public String getFamilyName() {
    return JavaAnalysisBundle.message("make.default.the.last.case.family.name");
  }

  @Override
  public @NotNull String getText() {
    return getFamilyName();
  }

  @Override
  public void invoke(@NotNull Project project,
                     @NotNull PsiFile file,
                     @Nullable Editor editor,
                     @NotNull PsiElement startElement,
                     @NotNull PsiElement endElement) {
    PsiSwitchLabelStatementBase labelStatementBase = (PsiSwitchLabelStatementBase)startElement;
    PsiSwitchBlock switchBlock = labelStatementBase.getEnclosingSwitchBlock();
    if (switchBlock == null) return;
    PsiCodeBlock blockBody = switchBlock.getBody();
    if (blockBody == null) return;
    PsiSwitchLabelStatementBase nextLabel =
      PsiTreeUtil.getNextSiblingOfType(labelStatementBase, PsiSwitchLabelStatementBase.class);//include comments and spaces
    if (nextLabel != null) {
      PsiElement lastStmtInDefaultCase = nextLabel.getPrevSibling();
      blockBody.addRange(labelStatementBase, lastStmtInDefaultCase);
      blockBody.deleteChildRange(labelStatementBase, lastStmtInDefaultCase);
    }
  }
}
