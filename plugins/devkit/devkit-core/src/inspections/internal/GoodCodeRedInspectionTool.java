// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.inspections.internal;

import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.compiler.CompilerConfiguration;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiUtilCore;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

abstract public class GoodCodeRedInspectionTool extends AbstractBaseJavaLocalInspectionTool {

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, boolean isOnTheFly) {
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

  @Nullable
  protected abstract GoodCodeRedVisitor getGoodCodeRedVisitor(@NotNull PsiFile file);
}
