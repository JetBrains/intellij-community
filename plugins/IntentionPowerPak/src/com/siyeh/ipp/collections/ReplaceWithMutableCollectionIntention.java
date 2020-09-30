// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ipp.collections;

import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.util.ObjectUtils;
import com.siyeh.IntentionPowerPackBundle;
import com.siyeh.ipp.base.Intention;
import com.siyeh.ipp.base.PsiElementPredicate;
import com.siyeh.ipp.collections.ImmutableCollectionModelUtils.ImmutableCollectionModel;
import org.jetbrains.annotations.NotNull;

public class ReplaceWithMutableCollectionIntention extends Intention {

  @Override
  protected void processIntention(Editor editor, @NotNull PsiElement element) {
    PsiMethodCallExpression call = ObjectUtils.tryCast(element, PsiMethodCallExpression.class);
    if (call == null) return;
    ImmutableCollectionModel model = ImmutableCollectionModelUtils.createModel(call);
    if (model == null) return;
    ImmutableCollectionModelUtils.replaceWithMutable(model, editor);
  }

  @Override
  protected void processIntention(@NotNull PsiElement element) {
    processIntention(null, element);
  }

  @NotNull
  @Override
  public String getText() {
    return IntentionPowerPackBundle.message("replace.with.mutable.collection.intention.family.name");
  }

  @NotNull
  @Override
  protected PsiElementPredicate getElementPredicate() {
    return element -> {
      PsiMethodCallExpression call = ObjectUtils.tryCast(element, PsiMethodCallExpression.class);
      return call != null && ImmutableCollectionModelUtils.createModel(call) != null;
    };
  }
}
