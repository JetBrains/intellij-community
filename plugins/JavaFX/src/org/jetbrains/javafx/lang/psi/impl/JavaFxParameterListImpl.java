package org.jetbrains.javafx.lang.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.ResolveState;
import com.intellij.psi.scope.PsiScopeProcessor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.javafx.lang.parser.JavaFxElementTypes;
import org.jetbrains.javafx.lang.psi.JavaFxParameter;
import org.jetbrains.javafx.lang.psi.JavaFxParameterList;
import org.jetbrains.javafx.lang.psi.impl.resolve.JavaFxResolveUtil;
import org.jetbrains.javafx.lang.psi.stubs.JavaFxParameterListStub;

/**
 * Created by IntelliJ IDEA.
 * Author: Alexey.Ivanov
 * Date:   12.05.2010
 * Time:   16:40:15
 */
public class JavaFxParameterListImpl extends JavaFxBaseElementImpl<JavaFxParameterListStub> implements JavaFxParameterList {
  public JavaFxParameterListImpl(@NotNull ASTNode node) {
    super(node);
  }

  public JavaFxParameterListImpl(JavaFxParameterListStub stub) {
    super(stub, JavaFxElementTypes.PARAMETER_LIST);
  }

  @NotNull
  public JavaFxParameter[] getParameters() {
    return getStubOrPsiChildren(JavaFxElementTypes.FORMAL_PARAMETER, JavaFxParameter.EMPTY_ARRAY);
  }

  @Override
  public boolean processDeclarations(@NotNull final PsiScopeProcessor processor,
                                     @NotNull final ResolveState state,
                                     final PsiElement lastParent,
                                     @NotNull final PsiElement place) {
    return JavaFxResolveUtil.processElements(getParameters(), lastParent, processor, state);
  }
}
