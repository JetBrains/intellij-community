package com.intellij.lang.ant.refactoring;

import com.intellij.openapi.util.Condition;
import com.intellij.psi.PsiElement;
import com.intellij.lang.ant.PsiAntElement;

public class AntVetoRenameCondition implements Condition<PsiElement> {
  public boolean value(final PsiElement element) {
    return element instanceof PsiAntElement && !((PsiAntElement)element).canRename();
  }
}
