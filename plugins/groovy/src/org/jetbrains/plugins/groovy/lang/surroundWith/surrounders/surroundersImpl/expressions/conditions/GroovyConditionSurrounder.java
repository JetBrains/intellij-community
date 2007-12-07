package org.jetbrains.plugins.groovy.lang.surroundWith.surrounders.surroundersImpl.expressions.conditions;

import org.jetbrains.plugins.groovy.lang.surroundWith.surrounders.surroundersImpl.expressions.GroovyExpressionSurrounder;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import com.intellij.psi.*;

/**
 * User: Dmitry.Krasilschikov
 * Date: 30.07.2007
 */
abstract class GroovyConditionSurrounder extends GroovyExpressionSurrounder {
  protected boolean isApplicable(PsiElement element) {
    if (! (element instanceof GrExpression)) return false;

    GrExpression expression = (GrExpression) element;
    PsiType type = expression.getType();

    if (type == null) return false;
    return type.equals(PsiType.BOOLEAN) || PsiType.BOOLEAN.equals(PsiPrimitiveType.getUnboxedType(type));
  }
}
