// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.testframework.actions;

import com.intellij.lang.LanguageExtension;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Provides functionality for updating the expected value in tests from the diff view when comparing the expected value with the actual
 * value.
 */
public interface TestDiffProvider {
  LanguageExtension<TestDiffProvider> TEST_DIFF_PROVIDER_LANGUAGE_EXTENSION =
    new LanguageExtension<>("com.intellij.testDiffProvider");

  @Nullable
  PsiElement getInjectionLiteral(@NotNull Project project, @NotNull String stackTrace);
}
