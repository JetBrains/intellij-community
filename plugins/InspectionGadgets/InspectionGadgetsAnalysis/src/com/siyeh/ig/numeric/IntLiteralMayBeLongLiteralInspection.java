// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.numeric;

import com.intellij.psi.PsiPrimitiveType;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;

/**
 * @author Bas Leijdekkers
 */
public class IntLiteralMayBeLongLiteralInspection extends CastedLiteralMaybeJustLiteralInspection {

  @NotNull
  @Override
  String getSuffix() {
    return "L";
  }

  @NotNull
  @Override
  PsiType getTypeBeforeCast() {
    return PsiType.INT;
  }

  @NotNull
  @Override
  PsiPrimitiveType getCastType() {
    return PsiType.LONG;
  }
}
