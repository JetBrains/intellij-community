// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.path;

import com.intellij.lang.ASTNode;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.GrMethodCallImpl;

/**
 * @author ilyas
 */
public class GrMethodCallExpressionImpl extends GrMethodCallImpl implements GrMethodCallExpression, GrCallExpression {

  public GrMethodCallExpressionImpl(@NotNull ASTNode node) {
    super(node);
  }

  public String toString() {
    return "Method call";
  }

  @Override
  public void accept(@NotNull GroovyElementVisitor visitor) {
    visitor.visitMethodCallExpression(this);
  }

  @Override
  public GrExpression replaceClosureArgument(@NotNull GrClosableBlock closure, @NotNull GrExpression newExpr)
    throws IncorrectOperationException {
    if (newExpr instanceof GrClosableBlock) {
      return closure.replaceWithExpression(newExpr, true);
    }

    final GrClosableBlock[] closureArguments = getClosureArguments();
    final int i = ArrayUtil.find(closureArguments, closure);
    GrArgumentList argList = getArgumentList();

    if (argList.getText().isEmpty()) {
      argList = (GrArgumentList)argList.replace(GroovyPsiElementFactory.getInstance(getProject()).createArgumentList());
    }

    for (int j = 0; j < i; j++) {
      argList.add(closureArguments[j]);
      closureArguments[j].delete();
    }
    final GrExpression result = (GrExpression)argList.add(newExpr);
    closure.delete();
    return result;
  }

  @Override
  public boolean hasClosureArguments() {
    return findChildByClass(GrClosableBlock.class) != null;
  }

  @Override
  @NotNull
  public GrClosableBlock[] getClosureArguments() {
    return findChildrenByClass(GrClosableBlock.class);
  }
}
