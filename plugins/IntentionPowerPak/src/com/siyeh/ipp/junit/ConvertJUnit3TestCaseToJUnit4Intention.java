// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ipp.junit;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.siyeh.ig.junit.JUnit4AnnotatedMethodInJUnit3TestCaseInspection;
import com.siyeh.ipp.base.Intention;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ConvertJUnit3TestCaseToJUnit4Intention extends Intention {

  @NotNull
  @Override
  protected PsiElementPredicate getElementPredicate() {
    return new ConvertJUnit3TestCaseToJUnit4Predicate();
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  @Nullable
  @Override
  public PsiElement getElementToMakeWritable(@NotNull PsiFile currentFile) {
    return currentFile;
  }

  @Override
  protected void processIntention(@NotNull PsiElement element) {
    final PsiElement parent = element.getParent();
    if (!(parent instanceof PsiClass)) {
      return;
    }
    final PsiClass aClass = (PsiClass)parent;
    JUnit4AnnotatedMethodInJUnit3TestCaseInspection.convertJUnit3ClassToJUnit4(aClass);
  }
}
