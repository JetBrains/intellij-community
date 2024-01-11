// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.refactoring;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.usageView.UsageInfo;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Allows performing cleanup operations after refactoring is done e.g., optimize imports 
 */
public interface RefactoringHelper<T> {
  ExtensionPointName<RefactoringHelper> EP_NAME = ExtensionPointName.create("com.intellij.refactoring.helper");

  /**
   * Is invoked in ReadAction under modal progress before refactoring is performed
   * 
   * @return result of cleanup preparation so after refactoring is actually performed, {@link #performOperation(Project, Object)} could be executed
   */
  T prepareOperation(UsageInfo @NotNull [] usages);

  /**
   * Is used when refactoring provides {@link BaseRefactoringProcessor#getBeforeData()} with psiElement, 
   * otherwise {@link #prepareOperation(UsageInfo[])} is used instead
   */
  default T prepareOperation(UsageInfo @NotNull [] usages, @NotNull PsiElement primaryElement) {
    return prepareOperation(usages);
  }

  /**
   * Is used when refactoring provides {@link BaseRefactoringProcessor#getBeforeData()} with all elements,
   * otherwise {@link #prepareOperation(UsageInfo[])} is used instead
   */
  default T prepareOperation(UsageInfo @NotNull [] usages, List<@NotNull PsiElement> elements) {
    return prepareOperation(usages);
  }

  /**
   * Is invoked in EDT, without WriteAction after refactoring is performed
   * 
   * Performs actual cleanup based on prepared data
   */
  void performOperation(@NotNull Project project, T operationData);
}
