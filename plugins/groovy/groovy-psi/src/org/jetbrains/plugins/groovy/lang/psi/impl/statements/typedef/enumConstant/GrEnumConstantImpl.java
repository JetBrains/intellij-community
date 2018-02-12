// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.plugins.groovy.lang.psi.impl.statements.typedef.enumConstant;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrEnumConstantInitializer;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrEnumConstant;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyResolveResultImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.GrFieldImpl;
import org.jetbrains.plugins.groovy.lang.psi.stubs.GrFieldStub;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;

/**
 * @author: Dmitry.Krasilschikov
 * @date: 06.04.2007
 */
public class GrEnumConstantImpl extends GrFieldImpl implements GrEnumConstant {
  private final MyReference myReference = new MyReference();

  public GrEnumConstantImpl(@NotNull ASTNode node) {
    super(node);
  }

  public GrEnumConstantImpl(GrFieldStub stub) {
    super(stub, GroovyElementTypes.ENUM_CONSTANT);
  }

  public String toString() {
    return "Enumeration constant";
  }

  @Override
  public boolean hasModifierProperty(@NonNls @NotNull String property) {
    if (property.equals(PsiModifier.STATIC)) return true;
    if (property.equals(PsiModifier.PUBLIC)) return true;
    if (property.equals(PsiModifier.FINAL)) return true;
    return false;
  }

  @Override
  public void accept(GroovyElementVisitor visitor) {
    visitor.visitEnumConstant(this);
  }

  @Override
  @Nullable
  public GrTypeElement getTypeElementGroovy() {
    return null;
  }

  @Override
  @NotNull
  public PsiType getType() {
    return JavaPsiFacade.getInstance(getProject()).getElementFactory().createType(getContainingClass(), PsiSubstitutor.EMPTY);
  }

  @Nullable
  @Override
  public PsiType getDeclaredType() {
    return getType();
  }

  @Override
  @Nullable
  public PsiType getTypeGroovy() {
    return getType();
  }

  @Override
  public void setType(@Nullable PsiType type) {
    throw new RuntimeException("Cannot set type for enum constant");
  }

  @Override
  @Nullable
  public GrExpression getInitializerGroovy() {
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
  @Nullable
  public GrArgumentList getArgumentList() {
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

  @NotNull
  @Override
  public GrNamedArgument[] getNamedArguments() {
    final GrArgumentList argumentList = getArgumentList();
    return argumentList == null ? GrNamedArgument.EMPTY_ARRAY : argumentList.getNamedArguments();
  }

  @NotNull
  @Override
  public GrExpression[] getExpressionArguments() {
    final GrArgumentList argumentList = getArgumentList();
    return argumentList == null ? GrExpression.EMPTY_ARRAY : argumentList.getExpressionArguments();
  }

  @NotNull
  @Override
  public GroovyResolveResult[] getCallVariants(@Nullable GrExpression upToArgument) {
    return multiResolve(true);
  }

  @NotNull
  @Override
  public GrClosableBlock[] getClosureArguments() {
    return GrClosableBlock.EMPTY_ARRAY;
  }

  @NotNull
  @Override
  public JavaResolveResult resolveMethodGenerics() {
    return JavaResolveResult.EMPTY;
  }

  @Override
  @Nullable
  public GrEnumConstantInitializer getInitializingClass() {
    return findChildByClass(GrEnumConstantInitializer.class);
  }

  @NotNull
  @Override
  public PsiEnumConstantInitializer getOrCreateInitializingClass() {
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

  @NotNull
  @Override
  public GroovyResolveResult[] multiResolve(boolean incompleteCode) {
    PsiType[] argTypes = PsiUtil.getArgumentTypes(getFirstChild(), false);
    PsiClass clazz = getContainingClass();
    return ResolveUtil.getAllClassConstructors(clazz, PsiSubstitutor.EMPTY, argTypes, this);
  }

  @NotNull
  @Override
  public PsiClass getContainingClass() {
    PsiClass aClass = super.getContainingClass();
    assert aClass != null;
    return aClass;
  }

  private class MyReference implements PsiPolyVariantReference {
    @Override
    @NotNull
    public ResolveResult[] multiResolve(boolean incompleteCode) {
      return GrEnumConstantImpl.this.multiResolve(false);
    }

    @Override
    public PsiElement getElement() {
      return GrEnumConstantImpl.this;
    }

    @Override
    public TextRange getRangeInElement() {
      return getNameIdentifierGroovy().getTextRange().shiftRight(-getTextOffset());
    }

    @Override
    public PsiElement resolve() {
      return resolveMethod();
    }

    @NotNull
    public GroovyResolveResult advancedResolve() {
      return GrEnumConstantImpl.this.advancedResolve();
    }

    @Override
    @NotNull
    public String getCanonicalText() {
      return getContainingClass().getName();
    }

    @Override
    public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
      return getElement();
    }

    @Override
    public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
      throw new IncorrectOperationException("invalid operation");
    }

    @Override
    public boolean isReferenceTo(PsiElement element) {
      return element instanceof GrMethod && ((GrMethod)element).isConstructor() && getManager().areElementsEquivalent(resolve(), element);
    }

    @Override
    @NotNull
    public Object[] getVariants() {
      return ArrayUtil.EMPTY_OBJECT_ARRAY;
    }

    @Override
    public boolean isSoft() {
      return false;
    }
  }

  @Nullable
  @Override
  public Object computeConstantValue() {
    return this;
  }
}
