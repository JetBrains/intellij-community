package org.jetbrains.javafx.lang.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.ResolveState;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.javafx.lang.parser.JavaFxElementTypes;
import org.jetbrains.javafx.lang.psi.JavaFxImportList;
import org.jetbrains.javafx.lang.psi.JavaFxImportStatement;
import org.jetbrains.javafx.lang.psi.stubs.JavaFxImportListStub;

/**
 * Created by IntelliJ IDEA.
 * Author: Alexey.Ivanov
 * Date:   12.05.2010
 * Time:   16:40:15
 */
public class JavaFxImportListImpl extends JavaFxBaseElementImpl<JavaFxImportListStub> implements JavaFxImportList {
  public JavaFxImportListImpl(@NotNull final ASTNode node) {
    super(node);
  }

  public JavaFxImportListImpl(@NotNull final JavaFxImportListStub stub) {
    super(stub, JavaFxElementTypes.IMPORT_LIST);
  }

  @NotNull
  public JavaFxImportStatement[] getImportStatements() {
    return getStubOrPsiChildren(TokenSet.create(JavaFxElementTypes.IMPORT_STATEMENT), JavaFxImportStatement.EMPTY_ARRAY);
  }

  @Override
  public boolean processDeclarations(@NotNull final PsiScopeProcessor processor,
                                     @NotNull final ResolveState state,
                                     final PsiElement lastParent,
                                     @NotNull final PsiElement place) {
    for (JavaFxImportStatement importStatement : getImportStatements()) {
      if (importStatement != lastParent && !importStatement.processDeclarations(processor, state, this, place)) {
        return false;
      }
    }
    return true;
  }
}
