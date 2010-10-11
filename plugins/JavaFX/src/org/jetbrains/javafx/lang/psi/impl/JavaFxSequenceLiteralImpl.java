package org.jetbrains.javafx.lang.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.javafx.lang.parser.JavaFxElementTypes;
import org.jetbrains.javafx.lang.psi.JavaFxExpression;
import org.jetbrains.javafx.lang.psi.JavaFxPsiUtil;
import org.jetbrains.javafx.lang.psi.JavaFxSequenceLiteral;
import org.jetbrains.javafx.lang.psi.impl.types.JavaFxTypeUtil;

/**
 * Created by IntelliJ IDEA.
 * Author: Alexey.Ivanov
 * Date:   12.05.2010
 * Time:   17:42:18
 */
public class JavaFxSequenceLiteralImpl extends JavaFxBaseElementImpl implements JavaFxSequenceLiteral {
  public JavaFxSequenceLiteralImpl(@NotNull ASTNode node) {
    super(node);
  }

  @NotNull
  @Override
  public JavaFxExpression[] getElements() {
    return JavaFxPsiUtil.nodesToPsi(getNode().getChildren(JavaFxElementTypes.EXPRESSIONS), JavaFxExpression.EMPTY_ARRAY);
  }

  @Override
  public PsiType getType() {
    final JavaFxExpression[] elements = getElements();
    if (elements.length == 0) {
      return JavaFxTypeUtil.createSequenceType(JavaFxTypeUtil.getObjectClassType(getProject()));
    }
    return JavaFxTypeUtil.createSequenceType(elements[0].getType());
  }
}
