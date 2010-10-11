package org.jetbrains.javafx.lang.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiType;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.javafx.lang.lexer.JavaFxTokenTypes;
import org.jetbrains.javafx.lang.parser.JavaFxElementTypes;
import org.jetbrains.javafx.lang.psi.JavaFxExpression;
import org.jetbrains.javafx.lang.psi.JavaFxUnaryExpression;
import org.jetbrains.javafx.lang.psi.impl.types.JavaFxPrimitiveType;

/**
 * Created by IntelliJ IDEA.
 * Author: Alexey.Ivanov
 * Date:   12.05.2010
 * Time:   17:42:18
 */
public class JavaFxUnaryExpressionImpl extends JavaFxBaseElementImpl implements JavaFxUnaryExpression {
  public JavaFxUnaryExpressionImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  public JavaFxExpression getOperand() {
    return (JavaFxExpression)childToPsi(JavaFxElementTypes.EXPRESSIONS);
  }

  @Override
  public PsiType getType() {
    final ASTNode node = getNode().findChildByType(JavaFxTokenTypes.UNARY_OPERATORS);
    assert node != null;
    final IElementType elementType = node.getElementType();
    if (elementType == JavaFxTokenTypes.NOT_KEYWORD) {
      return JavaFxPrimitiveType.BOOLEAN;
    }
    final JavaFxExpression operand = getOperand();
    if (operand == null) {
      return null;
    }
    if (elementType == JavaFxTokenTypes.MINUS || elementType == JavaFxTokenTypes.REVERSE_KEYWORD) {
      return operand.getType();
    }
    if (elementType == JavaFxTokenTypes.PLUSPLUS || elementType == JavaFxTokenTypes.MINUSMINUS) {
      final PsiType type = operand.getType();
      if (type == JavaFxPrimitiveType.DURATION) {
        return null;
      }
      return type;
    }
    if (elementType == JavaFxTokenTypes.SIZEOF_KEYWORD) {
      return JavaFxPrimitiveType.INTEGER;
    }
    return null;
  }
}
