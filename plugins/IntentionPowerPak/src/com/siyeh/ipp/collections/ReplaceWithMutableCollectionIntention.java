// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ipp.collections;

import com.intellij.codeInsight.intention.BaseElementAtCaretIntentionAction;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLambdaExpression;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.IntentionPowerPackBundle;
import com.siyeh.ipp.collections.ImmutableCollectionModelUtils.ImmutableCollectionModel;
import org.jetbrains.annotations.NotNull;

public class ReplaceWithMutableCollectionIntention extends BaseElementAtCaretIntentionAction {

  @Override
  public @NotNull String getFamilyName() {
    return IntentionPowerPackBundle.message("replace.with.mutable.collection.intention.family.name");
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, @NotNull PsiElement element) throws IncorrectOperationException {
    PsiMethodCallExpression call =
      PsiTreeUtil.getParentOfType(element, PsiMethodCallExpression.class, true, PsiClass.class, PsiLambdaExpression.class);
    if (call == null) return;
    ImmutableCollectionModel model = ImmutableCollectionModelUtils.createModel(call);
    if (model == null) return;
    ImmutableCollectionModelUtils.replaceWithMutable(model, editor);
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
    if (!JavaLanguage.INSTANCE.equals(element.getLanguage())) return false;
    PsiMethodCallExpression call =
      PsiTreeUtil.getParentOfType(element, PsiMethodCallExpression.class, true, PsiClass.class, PsiLambdaExpression.class);
    if (call == null) return false;
    ImmutableCollectionModel model = ImmutableCollectionModelUtils.createModel(call);
    if (model == null) return false;
    setText(IntentionPowerPackBundle.message("replace.with.mutable.collection.intention.intention.name", model.getText()));
    return true;
  }
}
