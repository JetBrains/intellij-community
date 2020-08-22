// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.codeInspection.fixes;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.util.IntentionFamilyName;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.util.Function;
import com.intellij.util.Functions;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

public class RemoveElementQuickFix implements LocalQuickFix {

  private final @IntentionFamilyName String myName;
  private final Function<? super PsiElement, ? extends PsiElement> myElementFunction;

  public RemoveElementQuickFix(@IntentionFamilyName @NotNull String name) {
    this(name, Functions.identity());
  }

  public RemoveElementQuickFix(@IntentionFamilyName @NotNull String name,
                               @NotNull Function<? super PsiElement, ? extends PsiElement> function) {
    myName = name;
    myElementFunction = function;
  }

  @Nls
  @NotNull
  @Override
  public String getFamilyName() {
    return myName;
  }

  @Override
  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    PsiElement descriptorElement = descriptor.getPsiElement();
    if (descriptorElement == null) return;

    PsiElement elementToRemove = myElementFunction.fun(descriptorElement);
    if (elementToRemove == null) return;

    elementToRemove.delete();
  }
}
