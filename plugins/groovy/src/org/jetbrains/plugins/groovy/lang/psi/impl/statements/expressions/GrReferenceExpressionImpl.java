/*
 * Copyright 2000-2007 JetBrains s.r.o.
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

package org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.ResolveCache;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrCodeBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrReferenceElementImpl;
import static org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.GrReferenceExpressionImpl.Kind.*;
import org.jetbrains.plugins.groovy.lang.resolve.processors.ResolverProcessor;
import static org.jetbrains.plugins.groovy.lang.resolve.processors.ClassHint.*;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;

import java.util.EnumSet;

/**
 * @author ilyas
 */
public class GrReferenceExpressionImpl extends GrReferenceElementImpl implements GrReferenceExpression {
  public GrReferenceExpressionImpl(@NotNull ASTNode node) {
    super(node);
  }

  public String toString() {
    return "Reference expression";
  }

  @Nullable
  public PsiElement resolve() {
    ResolveResult[] results = ((PsiManagerEx) getManager()).getResolveCache().resolveWithCaching(this, RESOLVER, false, false);
    return results.length == 1 ? results[0].getElement() : null;
  }

  private static final MyResolver RESOLVER = new MyResolver();

  private static class MyResolver implements ResolveCache.PolyVariantResolver<GrReferenceExpressionImpl> {
    public GroovyResolveResult[] resolve(GrReferenceExpressionImpl psiReference, boolean incompleteCode) {
      GrReferenceExpressionImpl refExpr = (GrReferenceExpressionImpl) psiReference;
      GrExpression qualifier = refExpr.getQualifierExpression();
      String name = refExpr.getReferenceName();
      if (name == null) return null;
      if (qualifier == null) {
        ResolverProcessor processor = getResolveProcessor(refExpr, name);

        ResolveUtil.treeWalkUp(refExpr, processor);
        return processor.getCandidates();
      } else {
        //todo real stuff
      }

      return null;
    }
  }

  private static ResolverProcessor getResolveProcessor(GrReferenceExpressionImpl refExpr, String name) {
    Kind kind = refExpr.getKind();
    ResolverProcessor processor;
    if (kind == PROPERTY) {
      processor = new ResolverProcessor(name, EnumSet.of(ResolveKind.PROPERTY), refExpr);
    } else if (kind == TYPE_OR_PROPERTY) {
      processor = new ResolverProcessor(name, EnumSet.of(ResolveKind.PROPERTY, ResolveKind.CLASS), refExpr); //todo package?
    } else /*if (kind == METHOD)*/ {
      processor = new ResolverProcessor(name, EnumSet.of(ResolveKind.METHOD), refExpr);
    }
    return processor;
  }

  enum Kind {
    PROPERTY,
    METHOD,
    TYPE_OR_PROPERTY
  }

  private Kind getKind() {
    PsiElement parent = getParent();
    if (parent instanceof GrMethodCall) {
      return METHOD;
    } else if (parent instanceof GrStatement || parent instanceof GrCodeBlock) {
      return TYPE_OR_PROPERTY;
    }

    return PROPERTY;
  }

  public String getCanonicalText() {
    return ""; //todo
  }

  public PsiElement bindToElement(PsiElement element) throws IncorrectOperationException {
    throw new IncorrectOperationException("NIY"); //todo
  }

  public boolean isReferenceTo(PsiElement element) {
    if (element instanceof PsiNamedElement && Comparing.equal(((PsiNamedElement) element).getName(), getReferenceName())) {
      return element.equals(resolve());
    }
    return false;
  }

  public Object[] getVariants() {
    ResolverProcessor processor = getResolveProcessor(this, null);
    ResolveUtil.treeWalkUp(this, processor);
    GroovyResolveResult[] candidates = processor.getCandidates();
    if (candidates.length == 0) return PsiNamedElement.EMPTY_ARRAY;
    return ResolveUtil.mapToElements(candidates);
  }

  public boolean isSoft() {
    return getQualifierExpression() != null;  //todo rethink
  }

  public GrExpression getQualifierExpression() {
    return findChildByClass(GrExpression.class);
  }

  public GroovyResolveResult advanceResolve() {
    ResolveResult[] results = ((PsiManagerEx) getManager()).getResolveCache().resolveWithCaching(this, RESOLVER, false, false);
    return results.length == 1 ? (GroovyResolveResult) results[0] : GroovyResolveResult.EMPTY_RESULT;
  }

  @NotNull
  public ResolveResult[] multiResolve(boolean b) {
    return ((PsiManagerEx) getManager()).getResolveCache().resolveWithCaching(this, RESOLVER, false, false);
  }

}