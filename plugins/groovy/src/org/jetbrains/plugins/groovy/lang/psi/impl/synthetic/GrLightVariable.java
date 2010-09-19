package org.jetbrains.plugins.groovy.lang.psi.impl.synthetic;

import com.intellij.psi.*;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentLabel;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyPropertyUtils;

import java.util.Collections;
import java.util.List;

/**
* @author sergey.evdokimov
*/
public class GrLightVariable extends GrImplicitVariableImpl implements NavigatablePsiElement {

  private final List<PsiElement> myDeclarations;

  public GrLightVariable(PsiModifierList modifierList,
                       PsiManager manager,
                       @NonNls String name,
                       @NonNls @NotNull String type,
                       @NotNull PsiElement navigationElement) {
    this(modifierList, manager, name, type, Collections.singletonList(navigationElement), getDeclarationScope(navigationElement));
  }

  public GrLightVariable(PsiModifierList modifierList,
                       PsiManager manager,
                       @NonNls String name,
                       @NonNls @NotNull String type,
                       @NotNull List<PsiElement> declarations,
                       @NotNull PsiElement scope) {
    this(modifierList, manager, name, JavaPsiFacade.getElementFactory(manager.getProject()).createTypeFromText(type, scope), declarations, scope);
  }

  public GrLightVariable(PsiModifierList modifierList,
                       PsiManager manager,
                       @NonNls String name,
                       @NotNull PsiType type,
                       @NotNull PsiElement navigationElement) {
    this(modifierList, manager, name, type, Collections.singletonList(navigationElement), getDeclarationScope(navigationElement));
  }

  public GrLightVariable(PsiModifierList modifierList,
                       PsiManager manager,
                       @NonNls String name,
                       @NotNull PsiType type,
                       @NotNull List<PsiElement> declarations,
                       @NotNull PsiElement scope) {
    super(modifierList, manager, new GrLightIdentifier(manager, name), type, false, scope);
    assert declarations.size() > 0;
    this.myDeclarations = declarations;
  }

  private static PsiElement getDeclarationScope(PsiElement navigationElement) {
    return navigationElement.getContainingFile();
  }

  @NotNull
  @Override
  public PsiElement getNavigationElement() {
    return myDeclarations.get(0);
  }

  @Override
  public PsiFile getContainingFile() {
    return myDeclarations.get(0).getContainingFile();
  }

  @Override
  public boolean isValid() {
    for (PsiElement declaration : myDeclarations) {
      if (!declaration.isValid()) return false;
    }

    return true;
  }

  @NotNull
  @Override
  public SearchScope getUseScope() {
    return new LocalSearchScope(getDeclarationScope());
  }

  @Override
  public boolean isEquivalentTo(PsiElement another) {
    return myDeclarations.contains(another) || super.isEquivalentTo(another);
  }

  @Override
  public PsiElement setName(@NotNull String name) throws IncorrectOperationException {
    for (PsiElement declaration : myDeclarations) {
      if (declaration instanceof PsiNamedElement) {
        if (declaration instanceof PsiMethod) {
          name = GroovyPropertyUtils.getGetterNameNonBoolean(name);
        }
        ((PsiNamedElement)declaration).setName(name);
      }
      else if (declaration instanceof GrArgumentLabel) {
        ((GrArgumentLabel)declaration).setName(name);
      }
    }

    return getNameIdentifier().replace(new GrLightIdentifier(myManager, name));
  }

  
}
