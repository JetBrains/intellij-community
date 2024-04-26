// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.dev.codeInsight.internal;

import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.compiler.CompilerConfiguration;
import com.intellij.dev.codeInsight.internal.GoodCodeRedVisitor;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiUtilCore;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class GoodCodeRedInspectionTool extends AbstractBaseJavaLocalInspectionTool {

  @Override
  public @NotNull PsiElementVisitor buildVisitor(final @NotNull ProblemsHolder holder, boolean isOnTheFly) {
    if (isOnTheFly) {
      //disable good code red in the editor as there general highlighting pass already does the job and the inspection just mess things
      return PsiElementVisitor.EMPTY_VISITOR;
    }
    final PsiFile file = holder.getFile();
    if (InjectedLanguageManager.getInstance(file.getProject()).isInjectedFragment(file)) {
      return PsiElementVisitor.EMPTY_VISITOR;
    }
    final VirtualFile virtualFile = PsiUtilCore.getVirtualFile(file);
    if (virtualFile == null ||
        CompilerConfiguration.getInstance(holder.getProject()).isExcludedFromCompilation(virtualFile)) {
      return PsiElementVisitor.EMPTY_VISITOR;
    }

    GoodCodeRedVisitor visitor = getGoodCodeRedVisitor(file);
    if (visitor == null) {
      return PsiElementVisitor.EMPTY_VISITOR;
    }

    return visitor.createVisitor(holder);
  }

  protected abstract @Nullable GoodCodeRedVisitor getGoodCodeRedVisitor(@NotNull PsiFile file);
}
