package org.jetbrains.plugins.groovy.lang.psi.impl;

import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrParenthesizedExpr;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.GrExpressionImpl;
import com.intellij.util.IncorrectOperationException;
import com.intellij.lang.ASTNode;

/**
 * "?????? ???
 */
public class PsiImplUtil {
  public static GrExpression replaceExpression(GrExpression oldExpr, GrExpression newExpr) throws IncorrectOperationException {
    if (oldExpr.getParent() == null ||
        oldExpr.getParent().getNode() == null ||
        newExpr.getNode() == null) {
      throw new IncorrectOperationException();
    }
    // Remove unnecessary parentheses
    if (oldExpr.getParent() instanceof GrParenthesizedExpr &&
        newExpr instanceof GrReferenceExpression){
      return ((GrExpression) oldExpr.getParent()).replaceWithExpresssion(newExpr);
    }
    ASTNode parentNode = oldExpr.getParent().getNode();
    ASTNode newNode = newExpr.getNode();
    parentNode.replaceChild(oldExpr.getNode(), newNode);
    if (!(newNode.getPsi() instanceof GrExpression)){
      throw new IncorrectOperationException();
    }
    return ((GrExpression) newNode.getPsi());
  }
}
