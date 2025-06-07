// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions;

import com.intellij.lang.ASTNode;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiElementImpl;

public abstract class GrCallImpl extends GroovyPsiElementImpl implements GrCall {
  public GrCallImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  public @Nullable GrArgumentList getArgumentList() {
    return findChildByClass(GrArgumentList.class);
  }

  @Override
  public GrNamedArgument @NotNull [] getNamedArguments() {
    GrArgumentList argList = getArgumentList();
    return argList != null ? argList.getNamedArguments() : GrNamedArgument.EMPTY_ARRAY;
  }

  @Override
  public GrExpression @NotNull [] getExpressionArguments() {
    GrArgumentList argList = getArgumentList();
    return argList != null ? argList.getExpressionArguments() : GrExpression.EMPTY_ARRAY;
  }

  @Override
  public GrNamedArgument addNamedArgument(final GrNamedArgument namedArgument) throws IncorrectOperationException {
    GrArgumentList list = getArgumentList();
    assert list != null;
    if (list.getText().trim().isEmpty()) {
      final GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(getProject());
      final GrArgumentList newList = factory.createExpressionArgumentList();
      list = (GrArgumentList)list.replace(newList);
    }
    return list.addNamedArgument(namedArgument);
  }
}
