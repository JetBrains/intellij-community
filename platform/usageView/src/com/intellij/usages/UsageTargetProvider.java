// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.usages;

import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface UsageTargetProvider {

  default UsageTarget @Nullable [] getTargets(@NotNull Editor editor, @NotNull PsiFile file) {
    return null;
  }

  default UsageTarget @Nullable [] getTargets(@NotNull PsiElement psiElement) {
    return null;
  }
}
