package org.jetbrains.plugins.groovy.lang.psi.impl.synthetic;

import com.intellij.psi.*;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.SearchScope;
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
                       @NotNull PsiElement element) {
    super(modifierList, manager, name, type, getDeclarationScope(element));
    this.myElement = element;
  }

  public GrLightVariable(PsiModifierList modifierList,
                       PsiManager manager,
                       @NonNls String name,
                       @NotNull PsiType type,
                       @NotNull PsiElement element) {
    super(modifierList, manager, new GrLightIdentifier(manager, name), type, false, getDeclarationScope(element));
    this.myElement = element;
  }

  private static PsiElement getDeclarationScope(PsiElement navigationElement) {
    return navigationElement.getContainingFile();
  }

  @NotNull
  @Override
  public PsiElement getNavigationElement() {
    return myElement;
  }

  @Override
  public PsiFile getContainingFile() {
    return myElement.getContainingFile();
  }

  @NotNull
  @Override
  public SearchScope getUseScope() {
    return new LocalSearchScope(getDeclarationScope());
  }

  @Override
  public boolean isEquivalentTo(PsiElement another) {
    return another == myElement || super.isEquivalentTo(another);
  }
}
