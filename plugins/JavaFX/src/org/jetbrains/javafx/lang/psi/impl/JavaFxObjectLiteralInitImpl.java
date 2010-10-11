package org.jetbrains.javafx.lang.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.javafx.lang.parser.JavaFxElementTypes;
import org.jetbrains.javafx.lang.psi.JavaFxExpression;
import org.jetbrains.javafx.lang.psi.JavaFxObjectLiteralInit;
import org.jetbrains.javafx.lang.psi.JavaFxReferenceElement;

/**
 * Created by IntelliJ IDEA.
 *
 * @author: Alexey.Ivanov
 */
public class JavaFxObjectLiteralInitImpl extends JavaFxBaseElementImpl implements JavaFxObjectLiteralInit {
  public JavaFxObjectLiteralInitImpl(@NotNull ASTNode node) {
    super(node);
  }

  @NotNull
  @SuppressWarnings({"ConstantConditions"})
  @Override
  public JavaFxReferenceElement getReferenceElement() {
    return (JavaFxReferenceElement)childToPsi(JavaFxElementTypes.REFERENCE_ELEMENT);
  }

  @Override
  @Nullable
  public JavaFxExpression getInitializer() {
    return (JavaFxExpression)childToPsi(JavaFxElementTypes.EXPRESSIONS);
  }

  @Override
  public String getName() {
    return getReferenceElement().getName();
  }

  @Override
  public PsiElement setName(@NonNls @NotNull final String name) throws IncorrectOperationException {
    throw new UnsupportedOperationException();
  }
}
