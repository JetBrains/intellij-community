package org.jetbrains.javafx.lang.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import com.intellij.psi.ResolveState;
import com.intellij.psi.scope.PsiScopeProcessor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.javafx.lang.parser.JavaFxElementTypes;
import org.jetbrains.javafx.lang.psi.JavaFxParameterList;
import org.jetbrains.javafx.lang.psi.JavaFxSignature;
import org.jetbrains.javafx.lang.psi.JavaFxTypeElement;
import org.jetbrains.javafx.lang.psi.stubs.JavaFxSignatureStub;

/**
 * Created by IntelliJ IDEA.
 * @author: Alexey.Ivanov
 */
public class JavaFxSignatureImpl extends JavaFxBaseElementImpl<JavaFxSignatureStub> implements JavaFxSignature {
  public JavaFxSignatureImpl(@NotNull JavaFxSignatureStub stub) {
    super(stub, JavaFxElementTypes.FUNCTION_SIGNATURE);
  }

  public JavaFxSignatureImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  @NotNull
  public JavaFxParameterList getParameterList() {
    return getStubOrPsiChild(JavaFxElementTypes.PARAMETER_LIST);
  }

  @Nullable
  private JavaFxTypeElement getResultTypeNode() {
    return (JavaFxTypeElement)childToPsi(JavaFxElementTypes.TYPE_ELEMENTS);
  }

  @Override
  public PsiType getReturnType() {
    final JavaFxTypeElement resultTypeNode = getResultTypeNode();
    if (resultTypeNode != null) {
      return resultTypeNode.getType();
    }
    return null;
  }

  @Override
  public boolean processDeclarations(@NotNull PsiScopeProcessor processor,
                                     @NotNull ResolveState state,
                                     PsiElement lastParent,
                                     @NotNull PsiElement place) {
    final JavaFxParameterList parameterList = getParameterList();
    if (parameterList != lastParent && !parameterList.processDeclarations(processor, state, lastParent, place)) {
      return false;
    }
    return true;
  }
}
