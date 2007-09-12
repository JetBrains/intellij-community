package org.jetbrains.plugins.groovy.lang.psi.impl.synthetic;

import com.intellij.psi.*;
import com.intellij.psi.impl.light.LightElement;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author ven
 */
public class LightReferenceList extends LightElement implements PsiReferenceList {
  protected LightReferenceList(PsiManager manager) {
    super(manager);
  }

  @NonNls
  public String getText() {
    return null;
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    visitor.visitReferenceList(this);
  }

  public PsiElement copy() {
    return null;
  }

  @NotNull
  public PsiJavaCodeReferenceElement[] getReferenceElements() {
    return PsiJavaCodeReferenceElement.EMPTY_ARRAY;
  }

  @NotNull
  public PsiClassType[] getReferencedTypes() {
    return PsiClassType.EMPTY_ARRAY;
  }

  public String toString() {
    return "Light Reference List";
  }
}
