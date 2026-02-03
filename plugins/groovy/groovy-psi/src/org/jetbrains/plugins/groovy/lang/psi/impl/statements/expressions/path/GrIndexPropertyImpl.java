// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.path;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyReference;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrIndexProperty;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.GrExpressionImpl;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyIndexPropertyUtil;
import org.jetbrains.plugins.groovy.lang.resolve.api.GroovyMethodCallReference;
import org.jetbrains.plugins.groovy.lang.resolve.references.GrGetAtReference;
import org.jetbrains.plugins.groovy.lang.resolve.references.GrPutAtReference;

import static org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.T_Q;
import static org.jetbrains.plugins.groovy.lang.psi.util.GroovyIndexPropertyUtil.isClassLiteral;
import static org.jetbrains.plugins.groovy.lang.psi.util.GroovyIndexPropertyUtil.isSimpleArrayAccess;
import static org.jetbrains.plugins.groovy.lang.psi.util.GroovyLValueUtil.isLValue;
import static org.jetbrains.plugins.groovy.lang.psi.util.GroovyLValueUtil.isRValue;
import static org.jetbrains.plugins.groovy.lang.resolve.ReferencesKt.referenceArray;

public class GrIndexPropertyImpl extends GrExpressionImpl implements GrIndexProperty {

  private final GroovyMethodCallReference myRValueReference = new GrGetAtReference(this);
  private final GroovyMethodCallReference myLValueReference = new GrPutAtReference(this);

  public GrIndexPropertyImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  public @Nullable GroovyMethodCallReference getRValueReference() {
    return isRValue(this) && isIndexAccess() ? myRValueReference : null;
  }

  @Override
  public @Nullable GroovyMethodCallReference getLValueReference() {
    return isLValue(this) && isIndexAccess() ? myLValueReference : null;
  }

  @Override
  public GroovyReference @NotNull [] getReferences() {
    return referenceArray(getRValueReference(), getLValueReference());
  }

  @Override
  public void accept(@NotNull GroovyElementVisitor visitor) {
    visitor.visitIndexProperty(this);
  }

  @Override
  public String toString() {
    return "Property by index";
  }

  @Override
  public @NotNull GrExpression getInvokedExpression() {
    return findNotNullChildByClass(GrExpression.class);
  }

  @Override
  public @Nullable PsiElement getSafeAccessToken() {
    return findChildByType(T_Q);
  }

  @Override
  public @NotNull GrArgumentList getArgumentList() {
    return findNotNullChildByClass(GrArgumentList.class);
  }

  @Override
  public @Nullable PsiType getNominalType() {
    return GroovyIndexPropertyUtil.getSimpleArrayAccessType(this);
  }

  private boolean isIndexAccess() {
    return !isClassLiteral(this) && !isSimpleArrayAccess(this);
  }
}
