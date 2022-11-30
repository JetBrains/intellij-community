// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.testframework.actions;

import com.intellij.lang.LanguageExtension;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Provides the ability to replace the expected value in the diff viewer with the actual value when it is declared as a string literal in
 * code.
 */
public interface TestDiffProvider {
  LanguageExtension<TestDiffProvider> TEST_DIFF_PROVIDER_LANGUAGE_EXTENSION = new LanguageExtension<>("com.intellij.testDiffProvider");

  /**
   * Finds the expected literal for a failed test if it exists and null otherwise. The returned literal must be an injected element.
   * @param stackTrace The stacktrace which can be used to find the expected literal
   */
  @Nullable
  PsiElement findExpected(@NotNull Project project, @NotNull String stackTrace);
}
