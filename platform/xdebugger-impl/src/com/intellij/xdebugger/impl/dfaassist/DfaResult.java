// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.dfaassist;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

public class DfaResult {
  public static final @NotNull DfaResult EMPTY = new DfaResult(null, Map.of(), Set.of());
  public final @Nullable PsiFile file;
  public final @NotNull Map<PsiElement, DfaHint> hints;
  public final @NotNull Collection<TextRange> unreachable;

  public DfaResult(@Nullable PsiFile file, @NotNull Map<PsiElement, DfaHint> hints, @NotNull Collection<TextRange> unreachable) {
    this.file = file;
    this.hints = hints;
    this.unreachable = unreachable;
  }
}