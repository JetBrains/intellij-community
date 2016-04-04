/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.TypeConversionUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrConstructorInvocation;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyResolveResultImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.GrCallImpl;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;
import org.jetbrains.plugins.groovy.lang.resolve.processors.MethodResolverProcessor;

/**
 * User: Dmitry.Krasilschikov
 * Date: 29.05.2007
 */
public class GrConstructorInvocationImpl extends GrCallImpl implements GrConstructorInvocation {
  public GrConstructorInvocationImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  public void accept(GroovyElementVisitor visitor) {
    visitor.visitConstructorInvocation(this);
  }

  public String toString() {
    return "Constructor invocation";
  }

  @Override
  public boolean isSuperCall() {
    return getKeywordType() == GroovyTokenTypes.kSUPER;
  }

  @Override
  public boolean isThisCall() {
    return getKeywordType() == GroovyTokenTypes.kTHIS;
  }

  @Nullable
  private IElementType getKeywordType() {
    GrReferenceExpression keyword = getInvokedExpression();
    PsiElement refElement = keyword.getReferenceNameElement();
    if (refElement == null) return null;

    return refElement.getNode().getElementType();
  }


  @Override
  @NotNull
  public GrReferenceExpression getInvokedExpression() {
    return findNotNullChildByClass(GrReferenceExpression.class);
  }

  @Override
  @NotNull
  public GroovyResolveResult[] multiResolve(boolean incompleteCode) {
    PsiClass clazz = getDelegatedClass();
    if (clazz != null) {
      PsiType[] argTypes = PsiUtil.getArgumentTypes(getFirstChild(), false);
      PsiElementFactory factory = JavaPsiFacade.getInstance(getProject()).getElementFactory();
      PsiSubstitutor substitutor;
      if (isThisCall()) {
        substitutor = PsiSubstitutor.EMPTY;
      }
      else {
        PsiClass enclosing = PsiUtil.getContextClass(this);
        assert enclosing != null;
        substitutor = TypeConversionUtil.getSuperClassSubstitutor(clazz, enclosing, PsiSubstitutor.EMPTY);
      }
      PsiType thisType = factory.createType(clazz, substitutor);
      MethodResolverProcessor processor = new MethodResolverProcessor(clazz.getName(), this, true, thisType, argTypes, PsiType.EMPTY_ARRAY,
                                                                      incompleteCode);
      final ResolveState state = ResolveState.initial().put(PsiSubstitutor.KEY, substitutor);
      clazz.processDeclarations(processor, state, null, this);
      ResolveUtil.processNonCodeMembers(thisType, processor, getInvokedExpression(), state);

      return processor.getCandidates();
    }
    return GroovyResolveResult.EMPTY_ARRAY;
  }

  @Override
  public GroovyResolveResult[] multiResolveClass() {
    PsiClass aClass = getDelegatedClass();
    if (aClass == null) return GroovyResolveResult.EMPTY_ARRAY;

    return new GroovyResolveResult[]{new GroovyResolveResultImpl(aClass, this, null, PsiSubstitutor.EMPTY, true, true)};
  }

  @Override
  public PsiMethod resolveMethod() {
    return PsiImplUtil.extractUniqueElement(multiResolve(false));
  }

  @NotNull
  @Override
  public GroovyResolveResult advancedResolve() {
    return PsiImplUtil.extractUniqueResult(multiResolve(false));
  }

  @Override
  @Nullable
  public PsiClass getDelegatedClass() {
    PsiClass typeDefinition = PsiUtil.getContextClass(this);
    if (typeDefinition != null) {
      return isThisCall() ? typeDefinition : typeDefinition.getSuperClass();
    }
    return null;
  }

  @NotNull
  @Override
  public GroovyResolveResult[] getCallVariants(@Nullable GrExpression upToArgument) {
    return multiResolve(true);
  }
}
