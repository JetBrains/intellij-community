// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.util.dynamicMembers;

import com.intellij.psi.*;
import com.intellij.psi.impl.light.LightElement;
import com.intellij.util.IncorrectOperationException;
import icons.JetgroovyIcons;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.extensions.NamedArgumentDescriptor;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocComment;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrAccessorMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;

import javax.swing.*;
import java.util.Map;

/**
 * @author Maxim.Medvedev
 */
public class GrDynamicPropertyImpl extends LightElement implements GrField {
  private final GrField myField;
  private final PsiClass myContainingClass;
  private final PsiElement myNavigationalElement;

  public GrDynamicPropertyImpl(PsiClass containingClass, GrField field, PsiElement navigationalElement) {
    super(field.getManager(), field.getLanguage());
    myContainingClass = containingClass;
    if (navigationalElement != null) {
      myNavigationalElement = navigationalElement;
    }
    else {
      myNavigationalElement = field;
    }

    myField = field;
  }

  @Override
  public GrDocComment getDocComment() {
    return null;
  }

  @Override
  public PsiClass getContainingClass() {
    return myContainingClass;
  }

  @Override
  public boolean isDeprecated() {
    return false;
  }

  @NotNull
  @Override
  public PsiElement getNavigationElement() {
    return myNavigationalElement;
  }

  @Override
  public Icon getIcon(int flags) {
    return JetgroovyIcons.Groovy.Property;
  }

  @Override
  public PsiFile getContainingFile() {
    return myContainingClass != null ? myContainingClass.getContainingFile() : null;
  }


  @Override
  public String toString() {
    return "Dynamic Property: " + getName();
  }

  @Override
  @NotNull
  public String getName() {
    return myField.getName();
  }

  @Override
  @NotNull
  public PsiType getType() {
    return myField.getType();
  }

  @Override
  public GrModifierList getModifierList() {
    return myField.getModifierList();
  }

  @Override
  public PsiTypeElement getTypeElement() {
    return myField.getTypeElement();
  }

  @Override
  public boolean hasModifierProperty(@NonNls @NotNull String name) {
    return myField.hasModifierProperty(name);
  }

  @Override
  public PsiExpression getInitializer() {
    return myField.getInitializer();
  }

  @Override
  public void setInitializer(@Nullable PsiExpression initializer) throws IncorrectOperationException {
    throw new IncorrectOperationException("Cannot set initializer");
  }

  @Override
  @NotNull
  public PsiIdentifier getNameIdentifier() {
    return myField.getNameIdentifier();
  }

  @Override
  public boolean hasInitializer() {
    return myField.hasInitializer();
  }

  @Override
  public void normalizeDeclaration() throws IncorrectOperationException {
    throw new IncorrectOperationException("cannot normalize declaration");
  }

  @Override
  public Object computeConstantValue() {
    return null;
  }

  @Override
  public PsiElement setName(@NonNls @NotNull String name) throws IncorrectOperationException {
    return this;
  }

  @Override
  public String getText() {
    return null;
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {

  }

  @Override
  public PsiElement copy() {
    return null;
  }

  @Override
  @NotNull
  public PsiElement getNameIdentifierGroovy() {
    return myField.getNameIdentifierGroovy();
  }

  @Override
  public void accept(@NotNull GroovyElementVisitor visitor) {
  }

  @Override
  public void acceptChildren(@NotNull GroovyElementVisitor visitor) {
  }

  @Override
  public PsiType getTypeGroovy() {
    return myField.getTypeGroovy();
  }

  @Override
  public PsiType getDeclaredType() {
    return myField.getDeclaredType();
  }

  @Override
  public boolean isProperty() {
    return myField.isProperty();
  }

  @Override
  public GrExpression getInitializerGroovy() {
    return myField.getInitializerGroovy();
  }

  @Override
  public void setType(@Nullable PsiType type) throws IncorrectOperationException {
    throw new IncorrectOperationException("cannot set type to dynamic property");
  }

  @Override
  public GrAccessorMethod getSetter() {
    return myField.getSetter();
  }

  @Override
  @NotNull
  public GrAccessorMethod[] getGetters() {
    return myField.getGetters();
  }

  @Override
  public GrTypeElement getTypeElementGroovy() {
    return myField.getTypeElementGroovy();
  }

  @Override
  @NotNull
  public Map<String, NamedArgumentDescriptor> getNamedParameters() {
    return myField.getNamedParameters();
  }

  @Override
  public void setInitializerGroovy(GrExpression initializer) {
    throw new IncorrectOperationException("cannot set initializer to dynamic property!");
  }

  @Override
  public boolean isEquivalentTo(PsiElement another) {
    return another instanceof GrDynamicPropertyImpl &&
           myManager.areElementsEquivalent(myField, ((GrDynamicPropertyImpl)another).myField) &&
           myManager.areElementsEquivalent(myContainingClass, ((GrDynamicPropertyImpl)another).myContainingClass);
  }
}
