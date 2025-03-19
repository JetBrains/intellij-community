// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.codeInspection.fixes;

import com.intellij.codeInspection.util.IntentionFamilyName;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.util.Function;
import com.intellij.util.Functions;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

public class RemoveElementQuickFix extends PsiUpdateModCommandQuickFix {

  private final @IntentionFamilyName String myName;
  private final Function<? super PsiElement, ? extends PsiElement> myElementFunction;

  public RemoveElementQuickFix(@IntentionFamilyName @NotNull String name) {
    this(name, Functions.identity());
  }

  /**
   * The function must be pure
   */
  public RemoveElementQuickFix(@IntentionFamilyName @NotNull String name,
                               @NotNull Function<? super PsiElement, ? extends PsiElement> function) {
    myName = name;
    myElementFunction = function;
  }

  @Override
  public @Nls @NotNull String getFamilyName() {
    return myName;
  }

  @Override
  protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
    PsiElement elementToRemove = myElementFunction.fun(element);
    if (elementToRemove == null) return;

    elementToRemove.delete();
  }
}
