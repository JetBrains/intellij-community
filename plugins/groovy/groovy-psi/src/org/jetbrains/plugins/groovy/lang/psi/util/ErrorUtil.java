// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.util;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.PsiRecursiveElementVisitor;
import org.jetbrains.annotations.NotNull;

public final class ErrorUtil {

  private ErrorUtil() {
    super();
  }

  public static boolean containsError(PsiElement element) {
    final ErrorElementVisitor visitor = new ErrorElementVisitor();
    element.accept(visitor);
    return visitor.containsErrorElement();
  }

  private static class ErrorElementVisitor extends PsiRecursiveElementVisitor {
    private boolean containsErrorElement = false;

    @Override
    public void visitErrorElement(@NotNull PsiErrorElement element) {
      containsErrorElement = true;
    }

    public boolean containsErrorElement() {
      return containsErrorElement;
    }
  }
}
