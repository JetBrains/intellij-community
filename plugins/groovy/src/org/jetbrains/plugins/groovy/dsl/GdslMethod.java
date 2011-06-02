package org.jetbrains.plugins.groovy.dsl;

import com.intellij.psi.PsiMethod;

/**
 * @author peter
 */
public class GdslMethod {
  public final PsiMethod psiMethod;

  public GdslMethod(PsiMethod psiMethod) {
    this.psiMethod = psiMethod;
  }

  public String getName() {
    return psiMethod.getName();
  }
}
