/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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

  public GrDocComment getDocComment() {
    return null;
  }

  public PsiClass getContainingClass() {
    return myContainingClass;
  }

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

  @NotNull
  public String getName() {
    return myField.getName();
  }

  @NotNull
  public PsiType getType() {
    return myField.getType();
  }

  public GrModifierList getModifierList() {
    return myField.getModifierList();
  }

  public PsiTypeElement getTypeElement() {
    return myField.getTypeElement();
  }

  public boolean hasModifierProperty(@NonNls @NotNull String name) {
    return myField.hasModifierProperty(name);
  }

  public PsiExpression getInitializer() {
    return myField.getInitializer();
  }

  public void setInitializer(@Nullable PsiExpression initializer) throws IncorrectOperationException {
    throw new IncorrectOperationException("Cannot set initializer");
  }

  @NotNull
  public PsiIdentifier getNameIdentifier() {
    return myField.getNameIdentifier();
  }

  public boolean hasInitializer() {
    return myField.hasInitializer();
  }

  public void normalizeDeclaration() throws IncorrectOperationException {
    throw new IncorrectOperationException("cannot normalize declaration");
  }

  public Object computeConstantValue() {
    return null;
  }

  public PsiElement setName(@NonNls @NotNull String name) throws IncorrectOperationException {
    return this;
  }

  public String getText() {
    return null;
  }

  public void accept(@NotNull PsiElementVisitor visitor) {

  }

  public PsiElement copy() {
    return null;
  }

  @NotNull
  public PsiElement getNameIdentifierGroovy() {
    return myField.getNameIdentifierGroovy();
  }

  public void accept(GroovyElementVisitor visitor) {
  }

  public void acceptChildren(GroovyElementVisitor visitor) {
  }

  public PsiType getTypeGroovy() {
    return myField.getTypeGroovy();
  }

  public PsiType getDeclaredType() {
    return myField.getDeclaredType();
  }

  public boolean isProperty() {
    return myField.isProperty();
  }

  public GrExpression getInitializerGroovy() {
    return myField.getInitializerGroovy();
  }

  public void setType(@Nullable PsiType type) throws IncorrectOperationException {
    throw new IncorrectOperationException("cannot set type to dynamic property");
  }

  public GrAccessorMethod getSetter() {
    return myField.getSetter();
  }

  @NotNull
  public GrAccessorMethod[] getGetters() {
    return myField.getGetters();
  }

  public GrTypeElement getTypeElementGroovy() {
    return myField.getTypeElementGroovy();
  }

  @NotNull
  public Map<String, NamedArgumentDescriptor> getNamedParameters() {
    return myField.getNamedParameters();
  }

  @Override
  public void clearCaches() {
    myField.clearCaches();
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
