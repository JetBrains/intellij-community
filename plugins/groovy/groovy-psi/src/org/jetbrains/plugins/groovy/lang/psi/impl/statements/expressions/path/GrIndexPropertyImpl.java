// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.path;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyReference;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrIndexProperty;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.GrExpressionImpl;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyIndexPropertyUtil;
import org.jetbrains.plugins.groovy.lang.resolve.api.Argument;
import org.jetbrains.plugins.groovy.lang.resolve.references.GrGetAtReference;
import org.jetbrains.plugins.groovy.lang.resolve.references.GrIndexPropertyReference;
import org.jetbrains.plugins.groovy.lang.resolve.references.GrPutAtReference;

import static com.intellij.psi.util.CachedValueProvider.Result;
import static org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.T_Q;
import static org.jetbrains.plugins.groovy.lang.psi.util.GroovyIndexPropertyUtil.isClassLiteral;
import static org.jetbrains.plugins.groovy.lang.psi.util.GroovyIndexPropertyUtil.isSimpleArrayAccess;
import static org.jetbrains.plugins.groovy.lang.psi.util.GroovyLValueUtil.getRValue;
import static org.jetbrains.plugins.groovy.lang.psi.util.GroovyLValueUtil.isRValue;
import static org.jetbrains.plugins.groovy.lang.resolve.ReferencesKt.referenceArray;

/**
 * @author ilyas
 */
public class GrIndexPropertyImpl extends GrExpressionImpl implements GrIndexProperty {

  public GrIndexPropertyImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Nullable
  @Override
  public GroovyReference getRValueReference() {
    return CachedValuesManager.getCachedValue(this, () -> {
      GrIndexPropertyReference reference = isRValue(this) && isIndexAccess() ? new GrGetAtReference(this) : null;
      return Result.create(reference, PsiModificationTracker.MODIFICATION_COUNT);
    });
  }

  @Nullable
  @Override
  public GroovyReference getLValueReference() {
    return CachedValuesManager.getCachedValue(this, () -> {
      Argument rValue = getRValue(this);
      GrIndexPropertyReference reference = rValue != null && isIndexAccess() ? new GrPutAtReference(this, rValue) : null;
      return Result.create(reference, PsiModificationTracker.MODIFICATION_COUNT);
    });
  }

  @NotNull
  @Override
  public GroovyReference[] getReferences() {
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
  @NotNull
  public GrExpression getInvokedExpression() {
    return findNotNullChildByClass(GrExpression.class);
  }

  @Nullable
  @Override
  public PsiElement getSafeAccessToken() {
    return findChildByType(T_Q);
  }

  @Override
  @NotNull
  public GrArgumentList getArgumentList() {
    return findNotNullChildByClass(GrArgumentList.class);
  }

  @Nullable
  @Override
  public PsiType getNominalType() {
    return GroovyIndexPropertyUtil.getSimpleArrayAccessType(this);
  }

  private boolean isIndexAccess() {
    return !isClassLiteral(this) && !isSimpleArrayAccess(this);
  }
}
