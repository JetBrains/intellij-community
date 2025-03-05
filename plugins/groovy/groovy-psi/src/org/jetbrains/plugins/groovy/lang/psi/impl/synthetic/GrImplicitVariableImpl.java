// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.psi.impl.synthetic;

import com.intellij.psi.*;
import com.intellij.psi.impl.light.LightIdentifier;
import com.intellij.psi.impl.light.LightVariableBase;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyLanguage;

public class GrImplicitVariableImpl extends LightVariableBase implements GrImplicitVariable {
  public GrImplicitVariableImpl(PsiManager manager, PsiIdentifier nameIdentifier, @NotNull PsiType type, boolean writable, PsiElement scope) {
    super(manager, nameIdentifier, GroovyLanguage.INSTANCE, type, writable, scope);
  }

  public GrImplicitVariableImpl(PsiManager manager, @NonNls String name, @NonNls @NotNull String type, PsiElement scope) {
    this(manager, new GrLightIdentifier(manager, name), JavaPsiFacade.getElementFactory(manager.getProject()).
      createTypeFromText(type, scope), false, scope);
  }

  @Override
  protected PsiModifierList createModifierList() {
    return new GrLightModifierList(this);
  }

  @Override
  public String toString() {
    return "Specific implicit variable: " + getName();
  }

  @Override
  public @NotNull GrLightModifierList getModifierList() {
    return (GrLightModifierList)myModifierList;
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

    @Override
    public PsiElement replace(@NotNull PsiElement newElement) throws IncorrectOperationException {
      myTextInternal = newElement.getText();
      return newElement;
    }

    @Override
    public String getText() {
      return myTextInternal;
    }

    @Override
    public PsiElement copy() {
      return new GrLightIdentifier(getManager(), myTextInternal);
    }
  }

}
