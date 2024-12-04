package org.jetbrains.intellij.plugins.journey.util;

import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNameIdentifierOwner;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Predicate;

public final class PsiUtil {

  @Contract("null, _ -> null")
  public static @Nullable PsiElement tryFindParentOrNull(@Nullable PsiElement element, Predicate<@NotNull PsiElement> test) {
    if (element == null) return null;
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

  @Contract("null -> null")
  public static @Nullable PsiElement tryFindIdentifier(@Nullable PsiElement element) {
    if (element == null) return null;
    if (element instanceof PsiNameIdentifierOwner nameIdentifierOwner) {
      return ReadAction.compute(() -> nameIdentifierOwner.getNameIdentifier());
    }
    return null;
  }

}
