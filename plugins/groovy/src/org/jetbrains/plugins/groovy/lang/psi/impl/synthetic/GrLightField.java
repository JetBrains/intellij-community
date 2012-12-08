/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.lang.psi.impl.synthetic;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiType;
import com.intellij.psi.impl.ResolveScopeManager;
import com.intellij.psi.search.SearchScope;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.extensions.NamedArgumentDescriptor;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocComment;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrAccessorMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import java.util.Collections;
import java.util.Map;

/**
 * @author sergey.evdokimov
 */
public class GrLightField extends GrLightVariable implements GrField {

  protected GrAccessorMethod mySetter;
  protected GrAccessorMethod[] myGetters;

  protected boolean mySetterInitialized = false;

  private final PsiClass myContainingClass;

  public GrLightField(@NotNull PsiClass containingClass,
                      @NonNls String name,
                      @NotNull PsiType type,
                      @NotNull PsiElement element) {
    super(containingClass.getManager(), name, type, element);
    myContainingClass = containingClass;
  }

  public GrLightField(@NotNull PsiClass containingClass,
                      @NonNls String name,
                      @NotNull String type) {
    super(containingClass.getManager(), name, type, containingClass);
    myContainingClass = containingClass;
    setNavigationElement(this);
  }

  @Override
  public GrDocComment getDocComment() {
    return null;
  }

  @Override
  public boolean isDeprecated() {
    return false;
  }

  @NotNull
  @Override
  public SearchScope getUseScope() {
    return ResolveScopeManager.getElementUseScope(this);
  }

  @Override
  public PsiClass getContainingClass() {
    return myContainingClass;
  }

  @Override
  public void setInitializer(@Nullable PsiExpression initializer) throws IncorrectOperationException {
    throw new IncorrectOperationException();
  }

  @Override
  public boolean isProperty() {
    return PsiUtil.isProperty(this);
  }

  @Override
  public GrAccessorMethod getSetter() {
    if (mySetterInitialized) return mySetter;

    mySetter = GrAccessorMethodImpl.createSetterMethod(this);
    mySetterInitialized = true;

    return mySetter;
  }

  @NotNull
  @Override
  public GrAccessorMethod[] getGetters() {
    if (myGetters == null) {
      myGetters = GrAccessorMethodImpl.createGetterMethods(this);
    }

    return myGetters;
  }

  @NotNull
  @Override
  public Map<String, NamedArgumentDescriptor> getNamedParameters() {
    return Collections.emptyMap();
  }

  @Override
  public void clearCaches() {
    mySetterInitialized = false;
    mySetter = null;
    myGetters = null;
  }

  @Override
  public void setInitializerGroovy(GrExpression initializer) {
    throw new IncorrectOperationException("cannot set initializer to light field!");
  }

  @Override
  public GrExpression getInitializerGroovy() {
    return null;
  }

  @Override
  public void setType(@Nullable PsiType type) throws IncorrectOperationException {
    throw new IncorrectOperationException();
  }

  @Override
  public GrTypeElement getTypeElementGroovy() {
    return null;
  }

  @Override
  public PsiType getTypeGroovy() {
    return getType();
  }

  @Override
  public PsiType getDeclaredType() {
    return getType();
  }

  @NotNull
  @Override
  public PsiElement getNameIdentifierGroovy() {
    return myNameIdentifier;
  }

  @Override
  public void accept(GroovyElementVisitor visitor) {
    visitor.visitField(this);
  }

  @Override
  public void acceptChildren(GroovyElementVisitor visitor) {

  }

  @Override
  public PsiElement setName(@NotNull String name) throws IncorrectOperationException {
    PsiElement res = super.setName(name);
    clearCaches();
    return res;
  }

  @Override
  public boolean isEquivalentTo(PsiElement another) {
    if (super.isEquivalentTo(another)) return true;

    if (another instanceof GrLightField) {
      GrLightField otherField = (GrLightField)another;
      return otherField.myContainingClass == myContainingClass && getName().equals(otherField.getName());
    }

    return false;
  }

  @Override
  public PsiElement copy() {
    assert getNavigationElement() != this;
    GrLightField copy = new GrLightField(myContainingClass, getName(), getType(), getNavigationElement());
    copy.setCreatorKey(getCreatorKey());
    return copy;
  }
}
