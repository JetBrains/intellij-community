package org.jetbrains.javafx.lang.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.javafx.lang.parser.JavaFxElementTypes;
import org.jetbrains.javafx.lang.psi.JavaFxFunctionTypeElement;
import org.jetbrains.javafx.lang.psi.JavaFxSignature;
import org.jetbrains.javafx.lang.psi.impl.types.JavaFxFunctionTypeImpl;
import org.jetbrains.javafx.lang.psi.impl.types.JavaFxTypeUtil;

/**
 * Created by IntelliJ IDEA.
 * @author: Alexey.Ivanov
 */
public class JavaFxFunctionTypeElementImpl extends JavaFxBaseElementImpl implements JavaFxFunctionTypeElement {
  public JavaFxFunctionTypeElementImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Nullable
  @Override
  public JavaFxSignature getSignature() {
    return (JavaFxSignature)childToPsi(JavaFxElementTypes.FUNCTION_SIGNATURE);
  }

  @NotNull
  @Override
  public PsiType getType() {
    final JavaFxSignature signature = getSignature();
    if (signature != null) {
      return new JavaFxFunctionTypeImpl(signature);
    }
    return JavaFxTypeUtil.getObjectClassType(getProject());
  }
}
