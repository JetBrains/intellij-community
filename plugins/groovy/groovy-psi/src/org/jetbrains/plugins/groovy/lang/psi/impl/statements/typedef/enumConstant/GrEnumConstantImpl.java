// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.psi.impl.statements.typedef.enumConstant;

import com.intellij.lang.ASTNode;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.parser.GroovyStubElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrEnumConstantInitializer;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrEnumConstant;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyResolveResultImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.GrFieldImpl;
import org.jetbrains.plugins.groovy.lang.psi.stubs.GrFieldStub;
import org.jetbrains.plugins.groovy.lang.resolve.api.GroovyConstructorReference;
import org.jetbrains.plugins.groovy.lang.resolve.references.GrEnumConstructorReference;

import static org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.MODIFIER_LIST;

public class GrEnumConstantImpl extends GrFieldImpl implements GrEnumConstant {

  private final GroovyConstructorReference myReference = new GrEnumConstructorReference(this);

  public GrEnumConstantImpl(@NotNull ASTNode node) {
    super(node);
  }

  public GrEnumConstantImpl(GrFieldStub stub) {
    super(stub, GroovyStubElementTypes.ENUM_CONSTANT);
  }

  @Override
  public String toString() {
    return "Enumeration constant";
  }

  @Override
  public @Nullable GrModifierList getModifierList() {
    return getStubOrPsiChild(MODIFIER_LIST);
  }

  @Override
  public boolean hasModifierProperty(@NonNls @NotNull String property) {
    if (property.equals(PsiModifier.STATIC)) return true;
    if (property.equals(PsiModifier.PUBLIC)) return true;
    if (property.equals(PsiModifier.FINAL)) return true;
    return false;
  }

  @Override
  public void accept(@NotNull GroovyElementVisitor visitor) {
    visitor.visitEnumConstant(this);
  }

  @Override
  public @Nullable GrTypeElement getTypeElementGroovy() {
    return null;
  }

  @Override
  public @NotNull PsiType getType() {
    return JavaPsiFacade.getInstance(getProject()).getElementFactory().createType(getContainingClass(), PsiSubstitutor.EMPTY);
  }

  @Override
  public @Nullable PsiType getDeclaredType() {
    return getType();
  }

  @Override
  public @Nullable PsiType getTypeGroovy() {
    return getType();
  }

  @Override
  public void setType(@Nullable PsiType type) {
    throw new RuntimeException("Cannot set type for enum constant");
  }

  @Override
  public @Nullable GrExpression getInitializerGroovy() {
    return null;
  }

  @Override
  public boolean isProperty() {
    return false;
  }

  @Override
  public GroovyResolveResult[] multiResolveClass() {
    GroovyResolveResult result = new GroovyResolveResultImpl(getContainingClass(), this, null, PsiSubstitutor.EMPTY, true, true);
    return new GroovyResolveResult[]{result};
  }

  @Override
  public @Nullable GrArgumentList getArgumentList() {
    return findChildByClass(GrArgumentList.class);
  }

  @Override
  public GrNamedArgument addNamedArgument(final GrNamedArgument namedArgument) throws IncorrectOperationException {
    GrArgumentList list = getArgumentList();
    assert list != null;
    if (list.getText().trim().isEmpty()) {
      final GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(getProject());
      final GrArgumentList newList = factory.createArgumentList();
      list = (GrArgumentList)list.replace(newList);
    }
    return list.addNamedArgument(namedArgument);
  }

  @Override
  public GrNamedArgument @NotNull [] getNamedArguments() {
    final GrArgumentList argumentList = getArgumentList();
    return argumentList == null ? GrNamedArgument.EMPTY_ARRAY : argumentList.getNamedArguments();
  }

  @Override
  public GrExpression @NotNull [] getExpressionArguments() {
    final GrArgumentList argumentList = getArgumentList();
    return argumentList == null ? GrExpression.EMPTY_ARRAY : argumentList.getExpressionArguments();
  }

  @Override
  public GroovyResolveResult @NotNull [] getCallVariants(@Nullable GrExpression upToArgument) {
    return multiResolve(true);
  }

  @Override
  public @NotNull JavaResolveResult resolveMethodGenerics() {
    return JavaResolveResult.EMPTY;
  }

  @Override
  public @Nullable GrEnumConstantInitializer getInitializingClass() {
    return getStubOrPsiChild(GroovyStubElementTypes.ENUM_CONSTANT_INITIALIZER);
  }

  @Override
  public @NotNull PsiEnumConstantInitializer getOrCreateInitializingClass() {
    final GrEnumConstantInitializer initializingClass = getInitializingClass();
    if (initializingClass != null) return initializingClass;

    final GrEnumConstantInitializer initializer =
      GroovyPsiElementFactory.getInstance(getProject()).createEnumConstantFromText("foo{}").getInitializingClass();
    LOG.assertTrue(initializer != null);
    final GrArgumentList argumentList = getArgumentList();
    if (argumentList != null) {
      return (PsiEnumConstantInitializer)addAfter(initializer, argumentList);
    }
    else {
      return (PsiEnumConstantInitializer)addAfter(initializer, getNameIdentifierGroovy());
    }
  }

  @Override
  public PsiReference getReference() {
    return myReference;
  }

  @Override
  public PsiMethod resolveConstructor() {
    return resolveMethod();
  }

  @Override
  public GroovyResolveResult @NotNull [] multiResolve(boolean incompleteCode) {
    return myReference.multiResolve(incompleteCode);
  }

  @Override
  public @NotNull PsiClass getContainingClass() {
    PsiClass aClass = super.getContainingClass();
    assert aClass != null;
    return aClass;
  }

  @Override
  public @Nullable Object computeConstantValue() {
    return this;
  }

  @Override
  public @NotNull GroovyConstructorReference getConstructorReference() {
    return myReference;
  }
}
