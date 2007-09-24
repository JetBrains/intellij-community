/*
 * Copyright (c) 2007, Your Corporation. All Rights Reserved.
 */

package org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.PsiType;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.impl.*;

import java.util.Collections;

/**
 * @author ilyas
 */
public abstract class GrExpressionImpl extends GroovyPsiElementImpl implements GrExpression {
  public GrExpressionImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(GroovyElementVisitor visitor) {
    visitor.visitExpression(this);
  }

  @Nullable
  public PsiType getNominalType() {
    final TypeInferenceHelper helper = GroovyPsiManager.getInstance(getProject()).getTypeInferenceHelper();

    return helper.doInference(new Computable<PsiType>() {
      public PsiType compute() {
        return getType();
      }
    }, Collections.<String, PsiType>emptyMap());
  }

  public GrExpression replaceWithExpression(@NotNull GrExpression newExpr) throws IncorrectOperationException {
    return PsiImplUtil.replaceExpression(this, newExpr);
  }

  public PsiType getTypeByFQName(String fqName) {
    return getManager().getElementFactory().createTypeByFQClassName(fqName, getResolveScope());
  }
}
