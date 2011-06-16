/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.GrFieldImpl;
import org.jetbrains.plugins.groovy.lang.psi.stubs.GrFieldStub;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.lang.resolve.processors.MethodResolverProcessor;

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

  public boolean hasModifierProperty(@NonNls @NotNull String property) {
    if (property.equals(PsiModifier.STATIC)) return true;
    if (property.equals(PsiModifier.PUBLIC)) return true;
    if (property.equals(PsiModifier.FINAL)) return true;
    return false;
  }

  public void accept(GroovyElementVisitor visitor) {
    visitor.visitEnumConstant(this);
  }

  @Nullable
  public GrTypeElement getTypeElementGroovy() {
    return null;
  }

  @NotNull
  public PsiType getType() {
    return JavaPsiFacade.getInstance(getProject()).getElementFactory().createType(getContainingClass(), PsiSubstitutor.EMPTY);
  }

  @Nullable
  public PsiType getTypeGroovy() {
    return getType();
  }

  public void setType(@Nullable PsiType type) {
    throw new RuntimeException("Cannot set type for enum constant");
  }

  @Nullable
  public GrExpression getInitializerGroovy() {
    return null;
  }

  public boolean isProperty() {
    return false;
  }

  @NotNull
  public GroovyResolveResult resolveConstructorGenerics() {
    return PsiImplUtil.extractUniqueResult(multiResolveConstructor());
  }

  public GroovyResolveResult[] multiResolveConstructor() {
    return multiResolveConstructorImpl(false);
  }

  private GroovyResolveResult[] multiResolveConstructorImpl(boolean allVariants) {
    PsiType[] argTypes = PsiUtil.getArgumentTypes(getFirstChild(), false);
    PsiClass clazz = getContainingClass();
    assert clazz != null;
    PsiType thisType = JavaPsiFacade.getInstance(getProject()).getElementFactory().createType(clazz, PsiSubstitutor.EMPTY);
    MethodResolverProcessor processor =
      new MethodResolverProcessor(clazz.getName(), this, true, thisType, argTypes, PsiType.EMPTY_ARRAY, allVariants, false);
    clazz.processDeclarations(processor, ResolveState.initial(), null, this);
    return processor.getCandidates();
  }

  public GroovyResolveResult[] multiResolveClass() {
    final PsiClass psiClass = getContainingClass();
    GroovyResolveResult result = new GroovyResolveResultImpl(psiClass, this, PsiSubstitutor.EMPTY, true, true);
    return new GroovyResolveResult[]{result};
  }

  @Nullable
  public GrArgumentList getArgumentList() {
    return findChildByClass(GrArgumentList.class);
  }

  public GrNamedArgument addNamedArgument(final GrNamedArgument namedArgument) throws IncorrectOperationException {
    GrArgumentList list = getArgumentList();
    assert list != null;
    if (list.getText().trim().length() == 0) {
      final GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(getProject());
      final GrArgumentList newList = factory.createArgumentList();
      list = (GrArgumentList)list.replace(newList);
    }
    return list.addNamedArgument(namedArgument);
  }

  @Override
  public GrNamedArgument[] getNamedArguments() {
    final GrArgumentList argumentList = getArgumentList();
    return argumentList == null ? GrNamedArgument.EMPTY_ARRAY : argumentList.getNamedArguments();
  }

  @Override
  public GrExpression[] getExpressionArguments() {
    final GrArgumentList argumentList = getArgumentList();
    return argumentList == null ? GrExpression.EMPTY_ARRAY : argumentList.getExpressionArguments();

  }

  @NotNull
  @Override
  public GroovyResolveResult[] getCallVariants(@Nullable GrExpression upToArgument) {
    return multiResolveConstructorImpl(true);
  }

  @NotNull
  @Override
  public GrClosableBlock[] getClosureArguments() {
    return GrClosableBlock.EMPTY_ARRAY;
  }

  @Override
  public PsiMethod resolveMethod() {
    return PsiImplUtil.extractUniqueElement(multiResolveConstructor());
  }

  @NotNull
  @Override
  public JavaResolveResult resolveMethodGenerics() {
    return JavaResolveResult.EMPTY;
  }

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

  @NotNull
  @Override
  public GroovyResolveResult advancedResolve() {
    return resolveConstructorGenerics();
  }

  @Override
  public PsiMethod resolveConstructor() {
    return resolveMethod();
  }

  private class MyReference implements PsiPolyVariantReference {
    @NotNull
    public ResolveResult[] multiResolve(boolean incompleteCode) {
      return multiResolveConstructor();
    }

    public PsiElement getElement() {
      return GrEnumConstantImpl.this;
    }

    public TextRange getRangeInElement() {
      return getNameIdentifierGroovy().getTextRange().shiftRight(-getTextOffset());
    }

    public PsiElement resolve() {
      return resolveMethod();
    }

    @NotNull
    public GroovyResolveResult advancedResolve() {
      return resolveConstructorGenerics();
    }

    @NotNull
    public String getCanonicalText() {
      return getContainingClass().getName();
    }

    public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
      return getElement();
    }

    public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
      throw new IncorrectOperationException("invalid operation");
    }

    public boolean isReferenceTo(PsiElement element) {
      return element instanceof GrMethod && ((GrMethod)element).isConstructor() && getManager().areElementsEquivalent(resolve(), element);
    }

    @NotNull
    public Object[] getVariants() {
      return ArrayUtil.EMPTY_OBJECT_ARRAY;
    }

    public boolean isSoft() {
      return false;
    }
  }
}
