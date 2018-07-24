// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.plugins.groovy.lang.psi.impl.statements.clauses;

import com.intellij.lang.ASTNode;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrCondition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.clauses.GrTraditionalForClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameterList;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiElementImpl;

/**
 * @author ilyas
 */
public class GrTraditionalForClauseImpl extends GroovyPsiElementImpl implements GrTraditionalForClause {
  public GrTraditionalForClauseImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  public void accept(@NotNull GroovyElementVisitor visitor) {
    visitor.visitTraditionalForClause(this);
  }

  public String toString() {
    return "Traditional FOR clause";
  }

  @Override
  public GrParameter getDeclaredVariable() {
    return findChildByClass(GrParameter.class);
  }

  @Override
  public GrCondition getInitialization() {
    return getConditionInner(0);
  }

  @Override
  public GrExpression getCondition() {
    final GrCondition condition = getConditionInner(1);
    return condition instanceof GrExpression ? (GrExpression)condition : null;
  }

  @Override
  public GrExpression getUpdate() {
    final GrCondition condition = getConditionInner(2);
    return condition instanceof GrExpression ? (GrExpression)condition : null;
  }

  private GrCondition getConditionInner(final int i) {
    int passed = 0;
    boolean waitForSemicolon = false;

    for (ASTNode child = getNode().getFirstChildNode(); child != null; child = child.getTreeNext()) {
      if (child.getElementType() == GroovyTokenTypes.mSEMI) {
        if (waitForSemicolon) {
          waitForSemicolon = false;
        }
        else {
          if (passed == i) {
            return null;
          }

          passed++;
        }
      }
      else if (child.getPsi() instanceof GrCondition) {
        if (passed == i) {
          return (GrCondition)child.getPsi();
        }

        passed++;
        waitForSemicolon = true;
      }
    }

    return null;
  }

  @Override
  public GrParameter[] getParameters() {
    final GrParameter declaredVariable = getDeclaredVariable();
    return declaredVariable == null ? GrParameter.EMPTY_ARRAY : new GrParameter[]{declaredVariable};
  }

  @Override
  public GrParameterList getParameterList() {
    return null;
  }

  @Override
  public boolean isVarArgs() {
    throw new IncorrectOperationException("For clause cannot have varargs");
  }
}
