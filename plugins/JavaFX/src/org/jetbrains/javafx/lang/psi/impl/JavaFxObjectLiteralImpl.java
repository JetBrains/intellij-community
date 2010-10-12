package org.jetbrains.javafx.lang.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiType;
import com.intellij.psi.ResolveState;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.javafx.lang.parser.JavaFxElementTypes;
import org.jetbrains.javafx.lang.psi.*;
import org.jetbrains.javafx.lang.psi.impl.resolve.JavaFxResolveUtil;
import org.jetbrains.javafx.lang.psi.impl.types.JavaFxTypeUtil;

/**
 * Created by IntelliJ IDEA.
 * Author: Alexey.Ivanov
 * Date:   12.05.2010
 * Time:   17:42:18
 */
public class JavaFxObjectLiteralImpl extends JavaFxBaseElementImpl implements JavaFxObjectLiteral {
  public JavaFxObjectLiteralImpl(@NotNull ASTNode node) {
    super(node);
  }

  @SuppressWarnings({"ConstantConditions"})
  @Override
  @NotNull
  public JavaFxReferenceElement getReferenceElement() {
    return (JavaFxReferenceElement)childToPsi(JavaFxElementTypes.REFERENCE_EXPRESSION);
  }

  @NotNull
  @Override
  public JavaFxVariableDeclaration[] getVariableDeclarations() {
    return JavaFxPsiUtil
      .nodesToPsi(getNode().getChildren(TokenSet.create(JavaFxElementTypes.VARIABLE_DECLARATION)), JavaFxVariableDeclaration.EMPTY_ARRAY);
  }

  @NotNull
  @Override
  public JavaFxFunctionDefinition[] getFunctionDefinitions() {
    return JavaFxPsiUtil
      .nodesToPsi(getNode().getChildren(TokenSet.create(JavaFxElementTypes.FUNCTION_DEFINITION)), JavaFxFunctionDefinition.EMPTY_ARRAY);
  }

  @NotNull
  @Override
  public JavaFxObjectLiteralInit[] getInits() {
    return JavaFxPsiUtil
      .nodesToPsi(getNode().getChildren(TokenSet.create(JavaFxElementTypes.OBJECT_LITERAL_INIT)), JavaFxObjectLiteralInit.EMPTY_ARRAY);
  }

  @Override
  public String getName() {
    return getReferenceElement().getText();
  }

  @Override
  protected void acceptJavaFxVisitor(@NotNull JavaFxElementVisitor visitor) {
    visitor.visitObjectLiteral(this);
  }

  @Override
  public PsiType getType() {
    final PsiReference reference = getReferenceElement().getReference();
    if (reference != null) {
      return JavaFxTypeUtil.createType(reference.resolve());
    }
    return null;
  }

  // TODO: Should we process override variables?
  @Override
  public boolean processDeclarations(@NotNull final PsiScopeProcessor processor,
                                     @NotNull final ResolveState state,
                                     final PsiElement lastParent,
                                     @NotNull final PsiElement place) {
    if (!JavaFxResolveUtil.processElements(getVariableDeclarations(), lastParent, processor, state)) {
      return false;
    }
    if (!JavaFxResolveUtil.processElements(getFunctionDefinitions(), lastParent, processor, state)) {
      return false;
    }
    final PsiReference reference = getReferenceElement().getReference();
    if (reference != null) {
      final PsiElement resolveResult = reference.resolve();
      if (resolveResult != null) {
        return resolveResult.processDeclarations(processor, state, this, place);
      }
    }
    return true;
  }
}
