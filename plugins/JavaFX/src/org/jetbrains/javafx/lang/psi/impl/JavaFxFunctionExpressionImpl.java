package org.jetbrains.javafx.lang.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import com.intellij.psi.ResolveState;
import com.intellij.psi.scope.PsiScopeProcessor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.javafx.lang.parser.JavaFxElementTypes;
import org.jetbrains.javafx.lang.psi.JavaFxBlockExpression;
import org.jetbrains.javafx.lang.psi.JavaFxFunctionExpression;
import org.jetbrains.javafx.lang.psi.JavaFxSignature;
import org.jetbrains.javafx.lang.psi.impl.types.JavaFxFunctionTypeImpl;

/**
 * Created by IntelliJ IDEA.
 * Author: Alexey.Ivanov
 * Date:   12.05.2010
 * Time:   17:42:18
 */
public class JavaFxFunctionExpressionImpl extends JavaFxBaseElementImpl implements JavaFxFunctionExpression {
  public JavaFxFunctionExpressionImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  public JavaFxSignature getSignature() {
    return (JavaFxSignature)childToPsi(JavaFxElementTypes.FUNCTION_SIGNATURE);
  }

  @Override
  public PsiType getReturnType() {
    final JavaFxSignature signature = getSignature();
    if (signature != null) {
      final PsiType resultType = signature.getReturnType();
      if (resultType != null) {
        return resultType;
      }
    }
    return getCodeBlock().getType();
  }

  @Override
  public JavaFxBlockExpression getCodeBlock() {
    return (JavaFxBlockExpression)childToPsi(JavaFxElementTypes.BLOCK_EXPRESSION);
  }

  // TODO:
  @Override
  public PsiType getType() {
    return new JavaFxFunctionTypeImpl(this);
  }

  @Override
  public boolean processDeclarations(@NotNull final PsiScopeProcessor processor,
                                     @NotNull final ResolveState state,
                                     final PsiElement lastParent,
                                     @NotNull final PsiElement place) {
    final JavaFxSignature signature = getSignature();
    if (signature != null && signature != lastParent && !signature.processDeclarations(processor, state, lastParent, place)) {
      return false;
    }
    return true;
  }
}
