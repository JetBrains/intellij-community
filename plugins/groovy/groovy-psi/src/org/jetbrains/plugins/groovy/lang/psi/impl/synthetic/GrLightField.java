// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import org.jetbrains.plugins.groovy.lang.psi.util.GrClassImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import java.util.Collections;
import java.util.Map;

/**
 * @author sergey.evdokimov
 */
public class GrLightField extends GrLightVariable implements GrField {

  private PsiClass myContainingClass;

  public GrLightField(@NotNull PsiClass containingClass,
                      @NonNls String name,
                      @NotNull PsiType type,
                      @NotNull PsiElement navigationElement) {
    super(containingClass.getManager(), name, type, navigationElement);
    myContainingClass = containingClass;
  }

  public GrLightField(@NotNull PsiClass containingClass,
                      @NonNls String name,
                      @NotNull String type) {
    super(containingClass.getManager(), name, type, containingClass);
    myContainingClass = containingClass;
    setNavigationElement(this);
  }

  public GrLightField(@NotNull String name, @NotNull String type, @NotNull PsiElement context) {
    super(context.getManager(), name, type, context);
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

  public GrLightField setContainingClass(@NotNull PsiClass containingClass) {
    myContainingClass = containingClass;
    return this;
  }

  @Override
  public void setInitializer(@Nullable PsiExpression initializer) throws IncorrectOperationException {
    throw new IncorrectOperationException();
  }

  @Override
  public boolean isProperty() {
    return PsiUtil.isProperty(this);
  }

  @Nullable
  @Override
  public GrAccessorMethod getSetter() {
    return GrClassImplUtil.findSetter(this);
  }
  @NotNull
  @Override
  public GrAccessorMethod[] getGetters() {
    return GrClassImplUtil.findGetters(this);
  }

  @NotNull
  @Override
  public Map<String, NamedArgumentDescriptor> getNamedParameters() {
    return Collections.emptyMap();
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
  public void accept(@NotNull GroovyElementVisitor visitor) {
    visitor.visitField(this);
  }

  @Override
  public void acceptChildren(@NotNull GroovyElementVisitor visitor) {

  }

  @Override
  public PsiElement setName(@NotNull String name) throws IncorrectOperationException {
    PsiElement res = super.setName(name);
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
