package org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.arithmetic;

import com.intellij.lang.ASTNode;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.arithmetic.GrRangeExpression;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.GrBinaryExpressionImpl;

/**
 * @author ilyas
 */
public class GrRangeExpressionImpl extends GrBinaryExpressionImpl implements GrRangeExpression {
  private static final String INTEGER_FQ_NAME = "java.lang.Integer";
  private static final String INT_RANGE_FQ_NAME = "groovy.lang.IntRange";
  private static final String OBJECT_RANGE_FQ_NAME = "groovy.lang.ObjectRange";

  public GrRangeExpressionImpl(@NotNull ASTNode node) {
    super(node);
  }

  public PsiType getType() {
    PsiElementFactory factory = JavaPsiFacade.getInstance(getProject()).getElementFactory();
    GrExpression lop = getLeftOperand();
    if (lop != null && lop.getType() != null && INTEGER_FQ_NAME.equals(lop.getType().getCanonicalText())) {
      return factory.createTypeByFQClassName(INT_RANGE_FQ_NAME, getResolveScope());
    }
    return factory.createTypeByFQClassName(OBJECT_RANGE_FQ_NAME, getResolveScope());
  }

  public String toString() {
    return "Range expression";
  }
}
