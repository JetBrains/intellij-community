// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

// This is a generated file. Not intended for manual editing.
package com.intellij.devkit.apiDump.lang.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface ADField extends ADMember {

  @NotNull
  ADFieldReference getFieldReference();

  @Nullable
  ADModifiers getModifiers();

  @Nullable
  ADTypeReference getTypeReference();

  @NotNull
  PsiElement getColon();

  @NotNull
  PsiElement getMinus();

  @NotNull PsiElement getNavigationElement();

}
