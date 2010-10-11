package org.jetbrains.javafx.lang.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiType;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.javafx.lang.lexer.JavaFxTokenTypes;
import org.jetbrains.javafx.lang.parser.JavaFxElementTypes;
import org.jetbrains.javafx.lang.psi.JavaFxBinaryExpression;
import org.jetbrains.javafx.lang.psi.JavaFxExpression;
import org.jetbrains.javafx.lang.psi.impl.types.JavaFxPrimitiveType;

/**
 * Created by IntelliJ IDEA.
 * Author: Alexey.Ivanov
 * Date:   12.05.2010
 * Time:   17:42:18
 */
public class JavaFxBinaryExpressionImpl extends JavaFxBaseElementImpl implements JavaFxBinaryExpression {
  public JavaFxBinaryExpressionImpl(@NotNull ASTNode node) {
    super(node);
  }

  @NotNull
  @Override
  public IElementType getOperator() {
    //noinspection ConstantConditions
    return getNode().findChildByType(JavaFxTokenTypes.BINARY_OPERATORS).getElementType();
  }

  @NotNull
  @Override
  public JavaFxExpression getLeftOperand() {
    //noinspection ConstantConditions
    return (JavaFxExpression)childToPsi(JavaFxElementTypes.EXPRESSIONS, 0);
  }

  @Override
  public JavaFxExpression getRightOperand() {
    return (JavaFxExpression)childToPsi(JavaFxElementTypes.EXPRESSIONS, 1);
  }

  @Override
  public PsiType getType() {
    final JavaFxExpression rightOperand = getRightOperand();
    if (rightOperand != null) {
      final JavaFxExpression leftOperand = getLeftOperand();
      final IElementType operator = getOperator();
      final PsiType leftOperandType = leftOperand.getType();
      final PsiType rightOperandType = rightOperand.getType();
      if (leftOperandType instanceof JavaFxPrimitiveType && rightOperandType instanceof JavaFxPrimitiveType) {
        if (operator == JavaFxTokenTypes.AND_KEYWORD || operator == JavaFxTokenTypes.OR_KEYWORD) {
          return JavaFxPrimitiveType.BOOLEAN;
        }
        if (JavaFxTokenTypes.RELATIONAL_OPERATORS.contains(operator)) {
          return JavaFxPrimitiveType.BOOLEAN;
        }
        if (operator == JavaFxTokenTypes.MOD_KEYWORD) {
          return JavaFxPrimitiveType.INTEGER;
        }
        if (operator == JavaFxTokenTypes.PLUS || operator == JavaFxTokenTypes.MINUS) {
          if (leftOperandType == JavaFxPrimitiveType.NUMBER || rightOperandType == JavaFxPrimitiveType.NUMBER) {
            return JavaFxPrimitiveType.NUMBER;
          }
          if (leftOperandType == JavaFxPrimitiveType.DURATION || rightOperandType == JavaFxPrimitiveType.DURATION) {
            return JavaFxPrimitiveType.DURATION;
          }
          return JavaFxPrimitiveType.INTEGER;
        }
        if (operator == JavaFxTokenTypes.MULT) {
          if (leftOperandType == JavaFxPrimitiveType.DURATION || rightOperandType == JavaFxPrimitiveType.DURATION) {
            return JavaFxPrimitiveType.DURATION;
          }
          if (leftOperandType == JavaFxPrimitiveType.NUMBER || rightOperandType == JavaFxPrimitiveType.NUMBER) {
            return JavaFxPrimitiveType.NUMBER;
          }
          return JavaFxPrimitiveType.INTEGER;
        }
        if (operator == JavaFxTokenTypes.DIV) {
          if (leftOperandType == JavaFxPrimitiveType.DURATION || rightOperandType == JavaFxPrimitiveType.DURATION) {
            if (leftOperandType == rightOperandType) {
              return JavaFxPrimitiveType.NUMBER;
            }
            return JavaFxPrimitiveType.DURATION;
          }
          if (leftOperandType == JavaFxPrimitiveType.NUMBER || rightOperandType == JavaFxPrimitiveType.NUMBER) {
            return JavaFxPrimitiveType.NUMBER;
          }
          return JavaFxPrimitiveType.INTEGER;
        }
      }
      else if (operator == JavaFxTokenTypes.EQEQ || operator == JavaFxTokenTypes.NOTEQ) {
        return JavaFxPrimitiveType.BOOLEAN;
      }
    }
    return null;
  }
}
