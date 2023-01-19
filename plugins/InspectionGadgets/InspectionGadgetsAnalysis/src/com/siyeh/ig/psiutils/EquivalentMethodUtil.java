// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.psiutils;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.util.ObjectUtils;
import com.siyeh.ig.callMatcher.CallMatcher;
import org.jetbrains.annotations.Nullable;

final class EquivalentMethodUtil {

  private static final CallMatcher PATH_CONSTRUCTOR = CallMatcher.anyOf(
    CallMatcher.staticCall("java.nio.file.Path", "of"),
    CallMatcher.staticCall("java.nio.file.Paths", "get")
  );

  static boolean areEquivalentMethods(@Nullable PsiElement element1, @Nullable PsiElement element2) {
    PsiMethod method1 = ObjectUtils.tryCast(element1, PsiMethod.class);
    if (!PATH_CONSTRUCTOR.methodMatches(method1)) return false;
    PsiMethod method2 = ObjectUtils.tryCast(element2, PsiMethod.class);
    return PATH_CONSTRUCTOR.methodMatches(method2);
  }
}