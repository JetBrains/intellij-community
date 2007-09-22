/*
 * Copyright (c) 2007, Your Corporation. All Rights Reserved.
 */

package org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiType;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.impl.*;

import java.util.Collections;
import java.util.Map;

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

    try {
      final Map<String, PsiType> map = Collections.emptyMap();
      helper.setCurrentEnvironment(map);
      return getType();
    } finally {
      helper.setCurrentEnvironment(null);
    }
  }

  public GrExpression replaceWithExpression(@NotNull GrExpression newExpr) throws IncorrectOperationException {
    return PsiImplUtil.replaceExpression(this, newExpr);
  }

  public PsiType getTypeByFQName(String fqName) {
    return getManager().getElementFactory().createTypeByFQClassName(fqName, getResolveScope());
  }
}
