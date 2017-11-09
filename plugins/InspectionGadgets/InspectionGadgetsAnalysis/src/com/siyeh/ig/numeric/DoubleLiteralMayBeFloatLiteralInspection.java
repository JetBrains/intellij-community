// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.numeric;

import com.intellij.psi.PsiType;
import com.siyeh.InspectionGadgetsBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

/**
 * @author Bas Leijdekkers
 */
public class DoubleLiteralMayBeFloatLiteralInspection extends CastedLiteralMaybeJustLiteralInspection {

  @Override
  @Nls
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("double.literal.may.be.float.literal.display.name");
  }

  @NotNull
  @Override
  String getSuffix() {
    return "f";
  }

  @NotNull
  @Override
  PsiType getLiteralBeforeType() {
    return PsiType.DOUBLE;
  }

  @NotNull
  @Override
  PsiType getCastType() {
    return PsiType.FLOAT;
  }
}
