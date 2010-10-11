package org.jetbrains.javafx.lang.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.javafx.lang.parser.JavaFxElementTypes;
import org.jetbrains.javafx.lang.psi.JavaFxExpression;
import org.jetbrains.javafx.lang.psi.JavaFxRangeExpression;
import org.jetbrains.javafx.lang.psi.impl.types.JavaFxPrimitiveType;
import org.jetbrains.javafx.lang.psi.impl.types.JavaFxTypeUtil;
import org.jetbrains.javafx.lang.psi.types.JavaFxType;

/**
 * Created by IntelliJ IDEA.
 * Author: Alexey.Ivanov
 * Date:   12.05.2010
 * Time:   17:42:18
 */
public class JavaFxRangeExpressionImpl extends JavaFxBaseElementImpl implements JavaFxRangeExpression {
  public JavaFxRangeExpressionImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  @NotNull
  public JavaFxExpression getBeginExpression() {
    //noinspection ConstantConditions
    return (JavaFxExpression)childToPsi(JavaFxElementTypes.EXPRESSIONS, 0);
  }

  @Nullable
  @Override
  public JavaFxExpression getEndExpression() {
    return (JavaFxExpression)childToPsi(JavaFxElementTypes.EXPRESSIONS, 1);
  }

  @Override
  @Nullable
  public JavaFxExpression getStep() {
    return (JavaFxExpression)childToPsi(JavaFxElementTypes.EXPRESSIONS, 2);
  }

  @Override
  public PsiType getType() {
    final JavaFxExpression endExpression = getEndExpression();
    final JavaFxExpression step = getStep();
    JavaFxType elementType = JavaFxPrimitiveType.INTEGER;
    if (getBeginExpression().getType() == JavaFxPrimitiveType.NUMBER ||
        (endExpression != null && endExpression.getType() == JavaFxPrimitiveType.NUMBER) ||
        (step != null && step.getType() == JavaFxPrimitiveType.NUMBER)) {
      elementType = JavaFxPrimitiveType.NUMBER;
    }
    return JavaFxTypeUtil.createSequenceType(elementType);
  }
}
