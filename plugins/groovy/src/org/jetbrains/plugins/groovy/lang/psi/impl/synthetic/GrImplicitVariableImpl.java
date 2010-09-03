/*
 * Copyright (c) 2008, Your Corporation. All Rights Reserved.
 */

package org.jetbrains.plugins.groovy.lang.psi.impl.synthetic;

import com.intellij.psi.*;
import com.intellij.psi.impl.light.LightIdentifier;
import com.intellij.psi.impl.light.LightVariableBase;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author ilyas
 */
public class GrImplicitVariableImpl extends LightVariableBase implements GrImplicitVariable {
  private PsiModifierList myInnerModifierList;

  public GrImplicitVariableImpl(PsiModifierList modifierList, PsiManager manager, PsiIdentifier nameIdentifier, @NotNull PsiType type, boolean writable, PsiElement scope) {
    super(manager, nameIdentifier, type, writable, scope);
        myInnerModifierList = modifierList != null ? modifierList : myModifierList;
    }

  public GrImplicitVariableImpl(PsiModifierList modifierList, PsiManager manager, @NonNls String name, @NonNls @NotNull String type, PsiElement referenceExpression) {
    this(modifierList, manager, null, JavaPsiFacade.getElementFactory(manager.getProject()).
      createTypeFromText(type, referenceExpression), false, referenceExpression);
    myNameIdentifier = new GrLightIdentifier(myManager, name);
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor) visitor).visitImplicitVariable(this);
    }
  }

  public String toString() {
    return "Specific implicit variable: " + getName();
  }

  public void setInitializer(@Nullable PsiExpression initializer) throws IncorrectOperationException {
    throw new IncorrectOperationException();
  }

  @Override
  public PsiModifierList getModifierList() {
    return myInnerModifierList;
  }

  @Override
  public boolean hasModifierProperty(@NotNull String modifier) {
    return myInnerModifierList.hasModifierProperty(modifier);
  }

  @Override
  public boolean isEquivalentTo(PsiElement another) {
    return another == getNavigationElement() || super.isEquivalentTo(another);
  }

  protected static class GrLightIdentifier extends LightIdentifier {
    private String myTextInternal;

    public GrLightIdentifier(PsiManager manager, String name) {
      super(manager, name);
      myTextInternal = name;
    }

    public PsiElement replace(@NotNull PsiElement newElement) throws IncorrectOperationException {
      myTextInternal = newElement.getText();
      return newElement;
    }

    public String getText() {
      return myTextInternal;
    }

    public PsiElement copy() {
      return new LightIdentifier(getManager(), myTextInternal);
    }
  }

}
