/*
 * Copyright (c) 2008, Your Corporation. All Rights Reserved.
 */

package org.jetbrains.plugins.groovy.lang.psi.util;

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

  public GrImplicitVariableImpl(PsiManager manager, PsiIdentifier nameIdentifier, @NotNull PsiType type, boolean writable, PsiElement scope) {
    super(manager, nameIdentifier, type, writable, scope);
  }

  public GrImplicitVariableImpl(PsiManager manager, @NonNls String name, @NonNls String type, PsiElement referenceExpression) {
    this(manager, null, manager.getElementFactory().createTypeByFQClassName(type, manager.getProject().getAllScope()), false, referenceExpression);
    myNameIdentifier = new GrLightIdentifier(myManager, name);
  }


  public void accept(@NotNull PsiElementVisitor visitor) {
    visitor.visitImplicitVariable(this);
  }

  public String toString() {
    return "Specific implicit variable";
  }

  public void setInitializer(@Nullable PsiExpression initializer) throws IncorrectOperationException {
    throw new IncorrectOperationException();
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
