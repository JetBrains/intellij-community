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
package org.jetbrains.plugins.groovy.lang.psi.impl.statements;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrConstructorInvocation;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrThisSuperReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiElementImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyResolveResultImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.lang.resolve.NonCodeMembersContributor;
import org.jetbrains.plugins.groovy.lang.resolve.NonCodeMembersProcessor;
import org.jetbrains.plugins.groovy.lang.resolve.processors.MethodResolverProcessor;

/**
 * User: Dmitry.Krasilschikov
 * Date: 29.05.2007
 */
public class GrConstructorInvocationImpl extends GroovyPsiElementImpl implements GrConstructorInvocation {
  public GrConstructorInvocationImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(GroovyElementVisitor visitor) {
    visitor.visitConstructorInvocation(this);
  }

  public String toString() {
    return "Constructor invocation";
  }

  @NotNull
  public GrArgumentList getArgumentList() {
    return findChildByClass(GrArgumentList.class);
  }

  @Nullable
  public GrExpression removeArgument(final int number) {
    return getArgumentList().removeArgument(number);
  }

  public GrNamedArgument addNamedArgument(final GrNamedArgument namedArgument) throws IncorrectOperationException {
    return getArgumentList().addNamedArgument(namedArgument);
  }

  public boolean isSuperCall() {
    return findChildByType(GroovyElementTypes.SUPER_REFERENCE_EXPRESSION) != null;
  }

  public boolean isThisCall() {
    return findChildByType(GroovyElementTypes.THIS_REFERENCE_EXPRESSION) != null;
  }

  private static final TokenSet THIS_OR_SUPER_SET =
    TokenSet.create(GroovyElementTypes.THIS_REFERENCE_EXPRESSION, GroovyElementTypes.SUPER_REFERENCE_EXPRESSION);

  public GrThisSuperReferenceExpression getThisOrSuperKeyword() {
    return (GrThisSuperReferenceExpression)findChildByType(THIS_OR_SUPER_SET);
  }

  public GroovyResolveResult[] multiResolveConstructor() {
    PsiClass clazz = getDelegatedClass();
    if (clazz != null) {
      PsiType[] argTypes = PsiUtil.getArgumentTypes(getFirstChild(), false);
      PsiElementFactory factory = JavaPsiFacade.getInstance(getProject()).getElementFactory();
      PsiSubstitutor substitutor;
      if (isThisCall()) {
        substitutor = PsiSubstitutor.EMPTY;
      } else {
        GrTypeDefinition enclosing = getEnclosingClass();
        assert enclosing != null;
        substitutor = TypeConversionUtil.getSuperClassSubstitutor(clazz, enclosing, PsiSubstitutor.EMPTY);
      }
      PsiType thisType = factory.createType(clazz, substitutor);
      MethodResolverProcessor processor = new MethodResolverProcessor(clazz.getName(), this, true, thisType, argTypes, PsiType.EMPTY_ARRAY);
      clazz.processDeclarations(processor, ResolveState.initial().put(PsiSubstitutor.KEY, substitutor), null, this);

      for (NonCodeMembersProcessor membersProcessor : NonCodeMembersProcessor.EP_NAME.getExtensions()) {
        if (!membersProcessor.processNonCodeMembers(thisType, processor, this, true)) break;
      }
      NonCodeMembersContributor.runContributors(thisType, processor, this, ResolveState.initial());

      return processor.getCandidates();
    }
    return GroovyResolveResult.EMPTY_ARRAY;
  }

  public GroovyResolveResult[] multiResolveClass() {
    return new GroovyResolveResult[]{new GroovyResolveResultImpl(getDelegatedClass(), this, PsiSubstitutor.EMPTY, true, true)};
  }

  public PsiMethod resolveConstructor() {
    return PsiImplUtil.extractUniqueElement(multiResolveConstructor());
  }

  @NotNull
  public GroovyResolveResult resolveConstructorGenerics() {
    return PsiImplUtil.extractUniqueResult(multiResolveConstructor());
  }

  @Nullable
  public PsiClass getDelegatedClass() {
    GrTypeDefinition typeDefinition = getEnclosingClass();
    if (typeDefinition != null) {
      return isThisCall() ? typeDefinition : typeDefinition.getSuperClass();
    }
    return null;
  }

  private GrTypeDefinition getEnclosingClass() {
    return PsiTreeUtil.getParentOfType(this, GrTypeDefinition.class);
  }

  public PsiElement getElement() {
    return this;
  }

  public TextRange getRangeInElement() {
    return new TextRange(0, getThisOrSuperKeyword().getTextLength());
  }

  @Nullable
  public PsiElement resolve() {
    return resolveConstructor();
  }

  @NotNull
  public String getCanonicalText() {
    return getText(); //TODO
  }
}
