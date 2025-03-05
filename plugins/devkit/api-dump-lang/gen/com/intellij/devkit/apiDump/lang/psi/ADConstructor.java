// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

// This is a generated file. Not intended for manual editing.
package com.intellij.devkit.apiDump.lang.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface ADConstructor extends ADMember {

  @NotNull
  ADConstructorReference getConstructorReference();

  @Nullable
  ADModifiers getModifiers();

  @NotNull
  ADParameters getParameters();

  @Nullable
  ADTypeReference getTypeReference();

  @Nullable
  PsiElement getColon();

  @NotNull
  PsiElement getMinus();

  @NotNull PsiElement getNavigationElement();

}
