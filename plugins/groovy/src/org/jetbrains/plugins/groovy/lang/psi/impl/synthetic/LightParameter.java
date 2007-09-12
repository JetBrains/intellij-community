package org.jetbrains.plugins.groovy.lang.psi.impl.synthetic;

import com.intellij.psi.impl.light.LightVariableBase;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;

/**
 * @author ven
 */
public class LightParameter extends LightVariableBase implements PsiParameter {
  public static final LightParameter[] EMPTY_ARRAY = new LightParameter[0];

  public LightParameter(PsiManager manager, PsiIdentifier nameIdentifier, @NotNull PsiType type, PsiElement scope) {
    super(manager, nameIdentifier, type, false, scope);
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    visitor.visitParameter(this);
  }

  public String toString() {
    return "Light Parameter";
  }

  public boolean isVarArgs() {
    return false;
  }

  @NotNull
  public PsiAnnotation[] getAnnotations() {
    return PsiAnnotation.EMPTY_ARRAY;
  }

  public String getName() {
    return "p";
  }
}
