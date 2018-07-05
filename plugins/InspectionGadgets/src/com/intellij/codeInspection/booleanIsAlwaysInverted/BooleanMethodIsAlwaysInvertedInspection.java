// Copyright 2000-2017 JetBrains s.r.o.
// Use of this source code is governed by the Apache 2.0 license that can be
// found in the LICENSE file.
package com.intellij.codeInspection.booleanIsAlwaysInverted;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.refactoring.invertBoolean.InvertBooleanProcessor;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.fixes.InvertBooleanFix;
import org.jetbrains.annotations.NotNull;

public class BooleanMethodIsAlwaysInvertedInspection extends BooleanMethodIsAlwaysInvertedInspectionBase {
  @NotNull
  @Override
  protected LocalQuickFix getInvertBooleanFix(boolean onTheFly) {
    return new InvertBooleanFix(InspectionGadgetsBundle.message("invert.method.quickfix")) {
      @Override
      public void doFix(@NotNull PsiElement element) {
        if (onTheFly) {
          //show the dialog, suggest to rename
          super.doFix(element);
        }
        else {
          PsiElement elementToRefactor = getElementToRefactor(element);
          new InvertBooleanProcessor(elementToRefactor, ((PsiNamedElement)elementToRefactor).getName())
            .run();
        }
      }
    };
  }
}