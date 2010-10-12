package org.jetbrains.javafx.lang.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.javafx.lang.parser.JavaFxElementTypes;
import org.jetbrains.javafx.lang.psi.JavaFxExpression;
import org.jetbrains.javafx.lang.psi.JavaFxInClause;
import org.jetbrains.javafx.lang.psi.JavaFxParameter;
import org.jetbrains.javafx.lang.psi.JavaFxTypeElement;
import org.jetbrains.javafx.lang.psi.impl.types.JavaFxTypeUtil;
import org.jetbrains.javafx.lang.psi.stubs.JavaFxParameterStub;
import org.jetbrains.javafx.lang.psi.types.JavaFxSequenceType;

/**
 * Created by IntelliJ IDEA.
 *
 * @author: Alexey.Ivanov
 */
public class JavaFxParameterImpl extends JavaFxPresentableElementImpl<JavaFxParameterStub> implements JavaFxParameter {
  public JavaFxParameterImpl(@NotNull JavaFxParameterStub stub) {
    super(stub, JavaFxElementTypes.FORMAL_PARAMETER);
  }

  public JavaFxParameterImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  public JavaFxTypeElement getTypeElement() {
    return childToPsi(JavaFxElementTypes.TYPE_ELEMENTS);
  }

  @Override
  public String getName() {
    final JavaFxParameterStub stub = getStub();
    if (stub != null) {
      return stub.getName();
    }
    return super.getName();
  }

  public PsiElement setName(@NonNls @NotNull String name) throws IncorrectOperationException {
    throw new IncorrectOperationException();
  }

  @Override
  public PsiType getType() {
    final JavaFxTypeElement typeElement = getTypeElement();
    if (typeElement != null) {
      return typeElement.getType();
    }
    final PsiElement parent = getParent();
    if (parent instanceof JavaFxInClause) {
      final JavaFxExpression iteratedExpression = ((JavaFxInClause)parent).getIteratedExpression();
      if (iteratedExpression != null) {
        final PsiType type = iteratedExpression.getType();
        if (type != null && type instanceof JavaFxSequenceType) {
          return ((JavaFxSequenceType)type).getElementType();
        }
      }
    }
    return JavaFxTypeUtil.getObjectClassType(getProject());
  }
}
