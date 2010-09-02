package org.jetbrains.plugins.groovy.lang.psi.impl.synthetic;

import com.intellij.psi.*;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
* @author sergey.evdokimov
*/
public class GrLightVariable extends GrImplicitVariableImpl implements NavigatablePsiElement {

  private final PsiElement myElement;

  public GrLightVariable(PsiModifierList modifierList,
                       PsiManager manager,
                       @NonNls String name,
                       @NonNls @NotNull String type,
                       PsiElement element) {
    super(modifierList, manager, name, type, element);
    this.myElement = element;
  }

  public GrLightVariable(PsiModifierList modifierList,
                       PsiManager manager,
                       @NonNls String name,
                       @NotNull PsiType type,
                       PsiElement element) {
    super(modifierList, manager, new GrLightIdentifier(manager, name), type, false, element);
    this.myElement = element;
  }

  @NotNull
  @Override
  public PsiElement getNavigationElement() {
    return myElement;
  }

  @Override
  public boolean isEquivalentTo(PsiElement another) {
    return another == myElement || super.isEquivalentTo(another);
  }
}
