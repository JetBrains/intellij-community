// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.psi.impl.synthetic;

import com.intellij.openapi.util.NlsSafe;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiEllipsisType;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiType;
import com.intellij.psi.impl.light.LightVariableBuilder;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyLanguage;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifier.GrModifierConstant;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;

import java.util.Objects;

/**
 * @author ven
 */
public class GrLightParameter extends LightVariableBuilder<GrLightParameter> implements GrParameter {
  public static final GrLightParameter[] EMPTY_ARRAY = new GrLightParameter[0];
  private volatile boolean myOptional;
  private volatile GrModifierList myModifierList;
  private volatile GrExpression myInitializer;
  private final PsiElement myScope;
  private final GrTypeElement myTypeElement;
  private final PsiType myTypeGroovy;

  public GrLightParameter(@NlsSafe @NotNull String name, @Nullable PsiType type, @NotNull PsiElement scope) {
    super(scope.getManager(), name, getTypeNotNull(type, scope), GroovyLanguage.INSTANCE);
    myScope = scope;
    myModifierList = new GrLightModifierList(this);
    myTypeGroovy = type;
    myTypeElement = type == null ? null : new GrLightTypeElement(type, scope.getManager());
  }

  public GrLightParameter(@NotNull GrParameter parameter) {
    super(
      parameter.getManager(),
      ((GrVariable)parameter).getName(),
      getTypeNotNull(parameter.getType(), parameter),
      GroovyLanguage.INSTANCE
    );
    myScope = parameter;

    myTypeGroovy = parameter.getTypeGroovy();
    myTypeElement = myTypeGroovy == null ? null : new GrLightTypeElement(myTypeGroovy, parameter.getManager());
    myOptional = parameter.isOptional();
    GrLightModifierList modifierList = new GrLightModifierList(this);
    modifierList.copyModifiers(parameter);
    myModifierList = modifierList;
  }

  public void setModifierList(GrModifierList modifierList) {
    myModifierList = modifierList;
  }

  @NotNull
  private static PsiType getTypeNotNull(PsiType type, PsiElement scope) {
    return type != null ? type : TypesUtil.getJavaLangObject(scope);
  }

  @NotNull
  @Override
  public PsiElement getDeclarationScope() {
    return myScope;
  }

  @Override
  public boolean isVarArgs() {
    return getType() instanceof PsiEllipsisType;
  }

  @Override
  public GrTypeElement getTypeElementGroovy() {
    return myTypeElement;
  }

  @Override
  public GrExpression getInitializerGroovy() {
    return myInitializer;
  }

  @Override
  public PsiElement getContext() {
    return getDeclarationScope();
  }

  @Override
  public PsiFile getContainingFile() {
    return getDeclarationScope().getContainingFile();
  }

  public GrLightParameter setOptional(boolean optional) {
    myOptional = optional;
    return this;
  }

  @Override
  public boolean isOptional() {
    return myOptional;
  }

  @Nullable
  @Override
  public PsiElement getEllipsisDots() {
    return null;
  }

  @Override
  public void setType(@Nullable PsiType type) throws IncorrectOperationException {
    throw new IncorrectOperationException();
  }

  @Override
  @NotNull
  public PsiElement getNameIdentifierGroovy() {
    return null;
  }

  @Override
  public PsiType getTypeGroovy() {
    return myTypeGroovy;
  }

  @Override
  public PsiType getDeclaredType() {
    return myTypeGroovy;
  }

  @Override
  public void accept(@NotNull GroovyElementVisitor visitor) {
  }

  @Override
  public void acceptChildren(@NotNull GroovyElementVisitor visitor) {

  }

  @Override
  public boolean isValid() {
    return getDeclarationScope().isValid();
  }

  @NotNull
  @Override
  public GrModifierList getModifierList() {
    return myModifierList;
  }

  @Override
  public void setInitializerGroovy(GrExpression initializer) {
    myInitializer = initializer;
  }

  @Override
  public GrLightParameter setModifiers(@GrModifierConstant String... modifiers) {
    GrLightModifierList modifiersList = new GrLightModifierList(getContext());
    modifiersList.setModifiers(modifiers);
    myModifierList = modifiersList;
    return this;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    GrLightParameter parameter = (GrLightParameter)o;
    return myOptional == parameter.myOptional &&
           Objects.equals(myModifierList, parameter.myModifierList) &&
           Objects.equals(myInitializer, parameter.myInitializer) &&
           Objects.equals(myTypeGroovy, parameter.myTypeGroovy);
  }

  @Override
  public int hashCode() {
    return Objects.hash(myOptional, myModifierList, myInitializer, myTypeGroovy);
  }
}
