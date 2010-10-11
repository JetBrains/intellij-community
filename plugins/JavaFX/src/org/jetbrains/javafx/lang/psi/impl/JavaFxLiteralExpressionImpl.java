package org.jetbrains.javafx.lang.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiType;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.javafx.lang.lexer.JavaFxTokenTypes;
import org.jetbrains.javafx.lang.psi.JavaFxElementVisitor;
import org.jetbrains.javafx.lang.psi.JavaFxLiteralExpression;
import org.jetbrains.javafx.lang.psi.impl.types.JavaFxPrimitiveType;
import org.jetbrains.javafx.lang.psi.impl.types.JavaFxTypeUtil;

/**
 * Created by IntelliJ IDEA.
 * Author: Alexey.Ivanov
 * Date:   12.05.2010
 * Time:   18:14:42
 */
public class JavaFxLiteralExpressionImpl extends JavaFxBaseElementImpl implements JavaFxLiteralExpression {
  public JavaFxLiteralExpressionImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void acceptJavaFxVisitor(@NotNull JavaFxElementVisitor visitor) {
    visitor.visitLiteralExpression(this);
  }

  @Override
  public PsiType getType() {
    final ASTNode astNode = getNode().findChildByType(JavaFxTokenTypes.LITERALS);
    assert astNode != null;
    final IElementType elementType = astNode.getElementType();
    if (elementType == JavaFxTokenTypes.INTEGER_LITERAL) {
      return JavaFxPrimitiveType.INTEGER;
    }
    if (elementType == JavaFxTokenTypes.NUMBER_LITERAL) {
      return JavaFxPrimitiveType.NUMBER;
    }
    if (elementType == JavaFxTokenTypes.DURATION_LITERAL) {
      return JavaFxPrimitiveType.DURATION;
    }
    if (elementType == JavaFxTokenTypes.NULL_KEYWORD) {
      return JavaFxTypeUtil.getObjectClassType(getProject());
    }
    if (elementType == JavaFxTokenTypes.TRUE_KEYWORD || elementType == JavaFxTokenTypes.FALSE_KEYWORD) {
      return JavaFxPrimitiveType.BOOLEAN;
    }
    return null;
  }
}
