/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.path;

import com.intellij.lang.ASTNode;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.GrMethodCallImpl;

import java.util.ArrayList;

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

  public void accept(GroovyElementVisitor visitor) {
    visitor.visitMethodCallExpression(this);
  }

  public GrExpression replaceClosureArgument(@NotNull GrClosableBlock closure, @NotNull GrExpression newExpr) throws IncorrectOperationException {

    ASTNode parentNode = this.getParent().getNode();
    if (!(newExpr instanceof GrClosableBlock)) {
      ArrayList<GrExpression> allArgs = new ArrayList<GrExpression>();
      // Collecting all arguments
      ContainerUtil.addAll(allArgs, getExpressionArguments());
      ArrayList<GrExpression> closureArgs = new ArrayList<GrExpression>();
      for (GrExpression closArg : getClosureArguments()) {
        if (closArg.equals(closure)) break;
        closureArgs.add(closArg);
      }
      allArgs.addAll(closureArgs);
      allArgs.add(newExpr);
      int refIndex = allArgs.size() - 1;

      // New argument list
      GrArgumentList newArgList =
        GroovyPsiElementFactory.getInstance(getProject()).createExpressionArgumentList(allArgs.toArray(new GrExpression[allArgs.size()]));
      while (closure.getNode().getTreePrev() != null &&
             !(closure.getNode().getTreePrev().getPsi() instanceof GrArgumentList)) {
        parentNode.removeChild(closure.getNode().getTreePrev());
      }
      parentNode.removeChild(closure.getNode());
      getArgumentList().replaceWithArgumentList(newArgList);
      GrExpression[] arguments = getArgumentList().getExpressionArguments();
      assert arguments.length == refIndex + 1;
      return arguments[refIndex];
    } else {
      return closure.replaceWithExpression(newExpr, true);
    }
  }

  @NotNull
  public GrClosableBlock[] getClosureArguments() {
    return findChildrenByClass(GrClosableBlock.class);
  }

  public GrExpression removeArgument(int number) {
    final GrArgumentList list = getArgumentList();
    final int exprLength = list.getExpressionArguments().length;
    if (exprLength > number) {
      return list.removeArgument(number);
    }
    else {
      number -= exprLength;
      for (int i = 0; i < getClosureArguments().length; i++) {
        GrClosableBlock block = getClosureArguments()[i];
        if (i == number) {
          final ASTNode node = block.getNode();
          getNode().removeChild(node);
          return block;
        }
      }
    }
    return null;
  }
}
