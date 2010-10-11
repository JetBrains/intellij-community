package org.jetbrains.javafx.lang.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiType;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.javafx.lang.lexer.JavaFxTokenTypes;
import org.jetbrains.javafx.lang.parser.JavaFxElementTypes;
import org.jetbrains.javafx.lang.psi.JavaFxExpression;
import org.jetbrains.javafx.lang.psi.JavaFxTypeElement;
import org.jetbrains.javafx.lang.psi.JavaFxTypeExpression;
import org.jetbrains.javafx.lang.psi.impl.types.JavaFxPrimitiveType;

/**
 * Created by IntelliJ IDEA.
 *
 * @author: Alexey.Ivanov
 */
public class JavaFxTypeExpressionImpl extends JavaFxBaseElementImpl implements JavaFxTypeExpression {
  public JavaFxTypeExpressionImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  @NotNull
  public JavaFxExpression getLeftOperand() {
    //noinspection ConstantConditions
    return (JavaFxExpression)childToPsi(JavaFxElementTypes.EXPRESSIONS);
  }

  @Override
  @NotNull
  public IElementType getOperator() {
    //noinspection ConstantConditions
    return getNode().findChildByType(JavaFxTokenTypes.BINARY_OPERATORS).getElementType();
  }

  @Override
  @Nullable
  public JavaFxTypeElement getTypeElement() {
    return (JavaFxTypeElement)childToPsi(JavaFxElementTypes.TYPE_ELEMENTS);
  }

  @Override
  public PsiType getType() {
    final JavaFxTypeElement typeElement = getTypeElement();
    if (typeElement != null) {
      final IElementType operator = getOperator();
      if (operator == JavaFxTokenTypes.INSTANCEOF_KEYWORD) {
        return JavaFxPrimitiveType.BOOLEAN;
      }
      if (operator == JavaFxTokenTypes.AS_KEYWORD) {
        return typeElement.getType();
      }
    }
    return null;
  }
}
