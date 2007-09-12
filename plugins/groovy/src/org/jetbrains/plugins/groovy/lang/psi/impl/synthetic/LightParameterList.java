package org.jetbrains.plugins.groovy.lang.psi.impl.synthetic;

import com.intellij.openapi.util.Computable;
import com.intellij.psi.*;
import com.intellij.psi.impl.light.LightElement;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author ven
 */
public class LightParameterList extends LightElement implements PsiParameterList {
  private Computable<LightParameter[]> myParametersComputation;
  private LightParameter[] myParameters = null;

  protected LightParameterList(PsiManager manager, Computable<LightParameter[]> parametersComputation) {
    super(manager);
    myParametersComputation = parametersComputation;
  }

  @NonNls
  public String getText() {
    return null;
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    visitor.visitParameterList(this);
  }

  public PsiElement copy() {
    return null;
  }

  @NotNull
  public PsiParameter[] getParameters() {
    if (myParameters == null) {
      myParameters = myParametersComputation.compute();
    }

    return myParameters;
  }

  public int getParameterIndex(PsiParameter parameter) {
    final PsiParameter[] parameters = getParameters();
    for (int i = 0; i < parameters.length; i++) {
      if (parameter.equals(parameters[i])) return i;
    }
    return -1;
  }

  public int getParametersCount() {
    return getParameters().length;
  }

  public String toString() {
    return "Light Parameter List";
  }
}
