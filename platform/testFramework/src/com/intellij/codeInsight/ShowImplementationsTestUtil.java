// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight;

import com.intellij.codeInsight.hint.ImplementationViewElement;
import com.intellij.codeInsight.hint.ImplementationViewSession;
import com.intellij.codeInsight.hint.PsiImplementationViewElement;
import com.intellij.codeInsight.hint.actions.ShowImplementationsAction;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiElement;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;


public final class ShowImplementationsTestUtil {
  public static PsiElement[] getImplementations() {
    return getImplementations(DataManager.getInstance().getDataContext());
  }

  public static PsiElement[] getImplementations(DataContext context) {
    final Ref<List<ImplementationViewElement>> ref = new Ref<>();
    new ShowImplementationsAction() {
      @Override
      protected void showImplementations(@NotNull ImplementationViewSession session,
                                         boolean invokedFromEditor,
                                         boolean invokedByShortcut) {
        ref.set(session.getImplementationElements());
      }
    }.performForContext(context);
    return ContainerUtil.map2Array(ref.get(), PsiElement.class, (element) -> ((PsiImplementationViewElement) element).getPsiElement());
  }
}
