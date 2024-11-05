package org.jetbrains.intellij.plugins.journey.util;

import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Predicate;

public final class PsiUtil {
  public static @Nullable PsiElement tryFindParentOrNull(PsiElement element, Predicate<@NotNull PsiElement> test) {
    return ReadAction.compute(() -> {
      var element1 = element;
      while (element1 != null) {
        if (test.test(element1)) {
          return element1;
        }
        element1 = element1.getParent();
      }
      return null;
    });
  }
}
