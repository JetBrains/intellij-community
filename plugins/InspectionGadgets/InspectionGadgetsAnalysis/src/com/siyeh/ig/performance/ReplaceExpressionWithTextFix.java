// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.performance;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.RemoveRedundantTypeArgumentsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNewExpression;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.CommentTracker;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class ReplaceExpressionWithTextFix extends InspectionGadgetsFix {
  private final @NotNull String myReplacementText;
  private final @NotNull @Nls(capitalization = Nls.Capitalization.Sentence) String myName;

  public ReplaceExpressionWithTextFix(@NotNull @NonNls String replacementText,
                                      @NotNull
                                      @Nls(capitalization = Nls.Capitalization.Sentence) String name) {
    myReplacementText = replacementText;
    myName = name;
  }


  @Override
  protected void doFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    PsiNewExpression newExpression = PsiTreeUtil.getParentOfType(descriptor.getStartElement(), PsiNewExpression.class);
    if (newExpression == null) return;
    PsiElement result = new CommentTracker().replaceAndRestoreComments(newExpression, myReplacementText);
    RemoveRedundantTypeArgumentsUtil.removeRedundantTypeArguments(result);
    JavaCodeStyleManager.getInstance(project).shortenClassReferences(result);
  }

  @Nls(capitalization = Nls.Capitalization.Sentence)
  @NotNull
  @Override
  public String getName() {
    return myName;
  }

  @Nls(capitalization = Nls.Capitalization.Sentence)
  @NotNull
  @Override
  public String getFamilyName() {
    return myName;
  }
}
