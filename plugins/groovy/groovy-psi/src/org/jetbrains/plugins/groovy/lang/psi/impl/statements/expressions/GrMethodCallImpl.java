// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions;

import com.intellij.lang.ASTNode;
import com.intellij.navigation.ItemPresentation;
import com.intellij.navigation.ItemPresentationProviders;
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
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.path.GrCallExpressionImpl;
import org.jetbrains.plugins.groovy.lang.resolve.api.GroovyMethodCallReference;
import org.jetbrains.plugins.groovy.lang.resolve.impl.GrImplicitCallReference;
import org.jetbrains.plugins.groovy.lang.resolve.references.GrExplicitMethodCallReference;

import static org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtilKt.isExplicitCall;
import static org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtilKt.isImplicitCall;

/**
 * @author Maxim.Medvedev
 */
public abstract class GrMethodCallImpl extends GrCallExpressionImpl implements GrMethodCall {

  private final GroovyMethodCallReference myImplicitCallReference = new GrImplicitCallReference(this);
  private final GroovyMethodCallReference myExplicitCallReference = new GrExplicitMethodCallReference(this);

  public GrMethodCallImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Nullable
  @Override
  public GroovyMethodCallReference getImplicitCallReference() {
    return isImplicitCall(this) ? myImplicitCallReference : null;
  }

  @Nullable
  @Override
  public GroovyMethodCallReference getExplicitCallReference() {
    return isExplicitCall(this) ? myExplicitCallReference : null;
  }

  @Nullable
  @Override
  public GroovyMethodCallReference getCallReference() {
    GroovyMethodCallReference explicitCallReference = getExplicitCallReference();
    return explicitCallReference == null ? getImplicitCallReference() : explicitCallReference;
  }

  @Override
  public GroovyResolveResult @NotNull [] getCallVariants(@Nullable GrExpression upToArgument) {
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

  @Override
  public GroovyResolveResult @NotNull [] multiResolve(boolean incompleteCode) {
    final GroovyMethodCallReference implicitCallReference = getImplicitCallReference();
    if (implicitCallReference != null) {
      return implicitCallReference.multiResolve(incompleteCode);
    }
    final GroovyMethodCallReference explicitCallReference = getExplicitCallReference();
    if (explicitCallReference != null) {
      return explicitCallReference.multiResolve(incompleteCode);
    }
    return GroovyResolveResult.EMPTY_ARRAY;
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
