// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.literals;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.GrExpressionImpl;


public abstract class GrAbstractLiteral extends GrExpressionImpl implements GrLiteral {

  public GrAbstractLiteral(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  public boolean isString() {
    PsiElement child = getFirstChild();
    if (child == null) return false;

    IElementType elementType = child.getNode().getElementType();
    return TokenSets.STRING_LITERAL_SET.contains(elementType);
  }
}
