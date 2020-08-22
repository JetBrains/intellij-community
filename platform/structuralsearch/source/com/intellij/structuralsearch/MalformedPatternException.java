// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch;

import com.intellij.psi.PsiErrorElement;
import org.jetbrains.annotations.NotNull;

public class MalformedPatternException extends StructuralSearchException {

  public final boolean isErrorElement;

  public MalformedPatternException() {
    isErrorElement = false;
  }

  public MalformedPatternException(@NotNull String msg) {
    super(msg);
    isErrorElement = false;
  }

  public MalformedPatternException(@NotNull PsiErrorElement errorElement) {
    super(errorElement.getErrorDescription());
    isErrorElement = true;
  }
}
