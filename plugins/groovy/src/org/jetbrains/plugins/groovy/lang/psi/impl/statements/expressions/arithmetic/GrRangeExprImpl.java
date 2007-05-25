
package org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.arithmetic;

import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.GrBinaryExpressionImpl;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.arithmetic.GrRangeExpression;
import org.jetbrains.annotations.NotNull;
import com.intellij.lang.ASTNode;

/**
 * @author ilyas
 */
public class GrRangeExprImpl extends GrBinaryExpressionImpl implements GrRangeExpression {

  public GrRangeExprImpl(@NotNull ASTNode node) {
    super(node);
  }

  public String toString() {
    return "Range expression";
  }
}
