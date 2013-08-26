package com.intellij.byteCodeViewer;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiTypeParameter;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

/**
 * Created by Max Medvedev on 8/23/13
 */
public class DefaultClassSearcher implements ClassSearcher {
  @Override
  public PsiClass findClass(@NotNull PsiElement psiElement) {
    PsiClass containingClass = PsiTreeUtil.getParentOfType(psiElement, PsiClass.class, false);
    while (containingClass instanceof PsiTypeParameter) {
      containingClass = PsiTreeUtil.getParentOfType(containingClass, PsiClass.class);
    }
    if (containingClass == null) return null;

    return containingClass;
  }
}
