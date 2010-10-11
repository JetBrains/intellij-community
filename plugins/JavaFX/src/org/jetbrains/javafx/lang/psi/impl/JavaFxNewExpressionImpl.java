package org.jetbrains.javafx.lang.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.javafx.lang.parser.JavaFxElementTypes;
import org.jetbrains.javafx.lang.psi.JavaFxNewExpression;
import org.jetbrains.javafx.lang.psi.JavaFxReferenceElement;
import org.jetbrains.javafx.lang.psi.impl.types.JavaFxTypeUtil;

/**
 * Created by IntelliJ IDEA.
 * Author: Alexey.Ivanov
 * Date:   12.05.2010
 * Time:   17:42:18
 */
public class JavaFxNewExpressionImpl extends JavaFxBaseElementImpl implements JavaFxNewExpression {
  public JavaFxNewExpressionImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Nullable
  @Override
  public JavaFxReferenceElement getReferenceElement() {
    return (JavaFxReferenceElement)childToPsi(JavaFxElementTypes.REFERENCE_ELEMENT);
  }

  @Override
  public PsiType getType() {
    final JavaFxReferenceElement referenceElement = getReferenceElement();
    if (referenceElement != null) {
      final PsiReference reference = referenceElement.getReference();
      if (reference != null) {
        return JavaFxTypeUtil.createType(reference.resolve());
      }
    }
    return null;
  }
}
