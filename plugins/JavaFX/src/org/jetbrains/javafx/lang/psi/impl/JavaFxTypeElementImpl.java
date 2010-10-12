package org.jetbrains.javafx.lang.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.javafx.lang.parser.JavaFxElementTypes;
import org.jetbrains.javafx.lang.psi.JavaFxReferenceElement;
import org.jetbrains.javafx.lang.psi.JavaFxTypeElement;
import org.jetbrains.javafx.lang.psi.impl.types.JavaFxTypeUtil;
import org.jetbrains.javafx.lang.psi.impl.types.JavaFxVoidType;

/**
 * Created by IntelliJ IDEA.
 *
 * @author: Alexey.Ivanov
 */
public class JavaFxTypeElementImpl extends JavaFxBaseElementImpl implements JavaFxTypeElement {
  public JavaFxTypeElementImpl(@NotNull ASTNode node) {
    super(node);
  }

  // TODO: array types
  @NotNull
  @Override
  public PsiType getType() {
    final JavaFxReferenceElement reference = (JavaFxReferenceElement) childToPsi(JavaFxElementTypes.REFERENCE_ELEMENT);
    if (reference == null) {
      return JavaFxVoidType.INSTANCE;
    }
    final PsiElement resolveResult = reference.getReference().resolve();
    PsiType type = JavaFxTypeUtil.createType(resolveResult);
    if (type != null && StringUtil.endsWith(getText(), "[]")) {
      type = JavaFxTypeUtil.createSequenceType(type);
    }
    return (type != null) ? type : JavaFxTypeUtil.getObjectClassType(getProject());
  }
}
