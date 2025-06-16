// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.dev.codeInsight.internal;

import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.dev.codeInsight.internal.GoodCodeRedVisitor;
import com.intellij.java.codeserver.highlighting.JavaErrorCollector;
import com.intellij.java.codeserver.highlighting.errors.JavaCompilationError;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import org.jetbrains.annotations.NotNull;

final class JavaGoodCodeRedVisitor implements GoodCodeRedVisitor {

  @Override
  public @NotNull PsiElementVisitor createVisitor(@NotNull ProblemsHolder holder) {
    return new PsiElementVisitor() {
      final JavaErrorCollector myErrorCollector = new JavaErrorCollector(holder.getFile(), this::report);

      private void report(@NotNull JavaCompilationError<?, ?> error) {
        int startOffset = error.range().getStartOffset();
        final PsiElement element = holder.getFile().findElementAt(startOffset);
        if (element != null) {
          holder.registerProblem(element, error.description());
        }
      }

      @Override
      public void visitElement(@NotNull PsiElement element) {
        myErrorCollector.processElement(element);
      }
    };
  }
}
