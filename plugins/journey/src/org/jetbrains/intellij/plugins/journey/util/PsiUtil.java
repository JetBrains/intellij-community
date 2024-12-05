package org.jetbrains.intellij.plugins.journey.util;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiNameIdentifierOwner;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Predicate;

import static com.intellij.ide.actions.QualifiedNameProviderUtil.getQualifiedName;

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

  public static @Nullable @NlsSafe String tryGetPresentableTitle(PsiElement element) {
    if (element instanceof PsiClass psiClass) {
      return ReadAction.compute(() -> psiClass.getName());
    }
    if (element instanceof PsiMember psiMember) {
      return ReadAction.compute(() -> {
        String name = psiMember.getName();
        PsiClass containingClass = psiMember.getContainingClass();
        if (containingClass == null) {
          return name;
        }
        else return containingClass.getName() + "." + name;
      });
    }
    return getQualifiedName(element);
  }

}
