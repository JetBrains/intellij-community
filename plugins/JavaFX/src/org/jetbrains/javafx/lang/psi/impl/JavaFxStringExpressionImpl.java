package org.jetbrains.javafx.lang.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.javafx.lang.parser.JavaFxElementTypes;
import org.jetbrains.javafx.lang.psi.JavaFxElementVisitor;
import org.jetbrains.javafx.lang.psi.JavaFxStringCompoundElement;
import org.jetbrains.javafx.lang.psi.JavaFxStringExpression;
import org.jetbrains.javafx.lang.psi.impl.types.JavaFxPrimitiveType;

/**
 * Created by IntelliJ IDEA.
 * Author: Alexey.Ivanov
 * Date:   12.05.2010
 * Time:   17:42:18
 */
public class JavaFxStringExpressionImpl extends JavaFxBaseElementImpl implements JavaFxStringExpression {
  public JavaFxStringExpressionImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  protected void acceptJavaFxVisitor(@NotNull JavaFxElementVisitor visitor) {
    visitor.visitStringExpression(this);
  }

  public JavaFxStringCompoundElement[] getStringElements() {
    return findChildrenByType(JavaFxElementTypes.STRING_ELEMENT, JavaFxStringCompoundElement.class);
  }

  @Override
  public PsiType getType() {
    return JavaFxPrimitiveType.STRING;
  }
}
