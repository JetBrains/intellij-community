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
   * Is called before the refactoring is executed when the refactoring provides {@link BaseRefactoringProcessor#getBeforeData()}.
   * It prepares the data that will be passed into {@link #performOperation} later.
   * @param usages the usages found of the elements the refactoring will be called on
   * @param elements the elements the refactoring will be called on
   */
  T prepareOperation(UsageInfo @NotNull [] usages, @NotNull List<@NotNull PsiElement> elements);

  /**
   * Is invoked in EDT, without WriteAction after refactoring is performed
   * 
   * Performs actual cleanup based on prepared data
   */
  void performOperation(@NotNull Project project, T operationData);
}
