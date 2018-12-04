// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions;

import com.intellij.lang.ASTNode;
import com.intellij.navigation.ItemPresentation;
import com.intellij.navigation.ItemPresentationProviders;
import com.intellij.openapi.util.AtomicNullableLazyValue;
import com.intellij.openapi.util.NullableLazyValue;
import com.intellij.psi.PsiElement;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtilKt;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.path.GrCallExpressionImpl;
import org.jetbrains.plugins.groovy.lang.resolve.api.GroovyMethodCallReference;
import org.jetbrains.plugins.groovy.lang.resolve.impl.GrImplicitCallReference;

import static org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil.LOG;

/**
 * @author Maxim.Medvedev
 */
public abstract class GrMethodCallImpl extends GrCallExpressionImpl implements GrMethodCall {

  public GrMethodCallImpl(@NotNull ASTNode node) {
    super(node);
  }

  private final NullableLazyValue<GroovyMethodCallReference> myImplicitCallReference = AtomicNullableLazyValue.createValue(
    () -> PsiImplUtilKt.isImplicitCall(this) ? new GrImplicitCallReference(this) : null
  );

  @Nullable
  @Override
  public GroovyMethodCallReference getImplicitCallReference() {
    return myImplicitCallReference.getValue();
  }

  @Override
  @NotNull
  public GroovyResolveResult[] getCallVariants(@Nullable GrExpression upToArgument) {
    final GrExpression invoked = getInvokedExpression();
    if (!(invoked instanceof GrReferenceExpression)) return GroovyResolveResult.EMPTY_ARRAY;
    return ((GrReferenceExpression)invoked).multiResolve(true);
  }

  @Override
  @NotNull
  public GrExpression getInvokedExpression() {
    for (PsiElement cur = this.getFirstChild(); cur != null; cur = cur.getNextSibling()) {
      if (cur instanceof GrExpression) return (GrExpression)cur;
    }
    throw new IncorrectOperationException("invoked expression must not be null");
  }

  @Override
  public boolean isCommandExpression() {
    final GrExpression expression = getInvokedExpression();
    if (!(expression instanceof GrReferenceExpression) || ((GrReferenceExpression)expression).getQualifier() == null) return false;

    return ((GrReferenceExpression)expression).getDotToken() == null;
  }

  @NotNull
  @Override
  public GroovyResolveResult[] multiResolve(boolean incompleteCode) {
    final GroovyMethodCallReference implicitCallReference = myImplicitCallReference.getValue();
    if (implicitCallReference != null) {
      return implicitCallReference.multiResolve(incompleteCode);
    }
    final GrExpression invokedExpression = getInvokedExpression();
    if (invokedExpression instanceof GrReferenceExpression) {
      return ((GrReferenceExpression)invokedExpression).multiResolve(incompleteCode);
    }
    else {
      LOG.error("Invoked expression is not a reference expression and there is no implicit call reference: '" + getText() + "'");
      return GroovyResolveResult.EMPTY_ARRAY;
    }
  }

  @Override
  public ItemPresentation getPresentation() {
    return ItemPresentationProviders.getItemPresentation(this);
  }

  @NotNull
  @Override
  public GrArgumentList getArgumentList() {
    return findNotNullChildByClass(GrArgumentList.class);
  }

  @Override
  public void accept(@NotNull GroovyElementVisitor visitor) {
    visitor.visitMethodCall(this);
  }
}
