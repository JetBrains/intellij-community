// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.psi.impl.synthetic;

import com.intellij.lang.properties.psi.Property;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiType;
import com.intellij.psi.impl.ElementPresentationUtil;
import com.intellij.psi.impl.ResolveScopeManager;
import com.intellij.psi.search.SearchScope;
import com.intellij.ui.IconManager;
import com.intellij.ui.icons.RowIcon;
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

import javax.swing.*;
import java.util.Collections;
import java.util.Map;

public class GrLightField extends GrLightVariable implements GrField {

  private PsiClass myContainingClass;
  private Icon myIcon;

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

  @Override
  public @NotNull SearchScope getUseScope() {
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

  @Override
  public @Nullable GrAccessorMethod getSetter() {
    return GrClassImplUtil.findSetter(this);
  }
  @Override
  public GrAccessorMethod @NotNull [] getGetters() {
    return GrClassImplUtil.findGetters(this);
  }

  @Override
  public @NotNull Map<String, NamedArgumentDescriptor> getNamedParameters() {
    return Collections.emptyMap();
  }

  @Override
  public void setInitializerGroovy(GrExpression initializer) {
    throw new IncorrectOperationException("cannot set initializer to light field!");
  }

  @Override
  public Object computeConstantValue() {
    PsiElement navigationElement = getNavigationElement();
    if (navigationElement instanceof Property) {
      return ((Property)navigationElement).getKey();
    }
    return super.computeConstantValue();
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

  @Override
  public @NotNull PsiElement getNameIdentifierGroovy() {
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

    if (another instanceof GrLightField otherField) {
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

  public void setIcon(@NotNull Icon icon) {
    myIcon = icon;
  }

  @Override
  public Icon getElementIcon(int flags) {
    Icon actualIcon = myIcon == null ? super.getElementIcon(flags) : myIcon;
    RowIcon baseIcon = IconManager.getInstance().createLayeredIcon(this, actualIcon, ElementPresentationUtil.getFlags(this, false));
    return ElementPresentationUtil.addVisibilityIcon(this, flags, baseIcon);
  }
}
