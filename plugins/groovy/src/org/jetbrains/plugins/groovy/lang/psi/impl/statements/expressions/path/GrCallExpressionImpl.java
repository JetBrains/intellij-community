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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.GrExpressionImpl;

/**
 * @author ven
 */
public abstract class GrCallExpressionImpl extends GrExpressionImpl implements GrCallExpression{
  public GrCallExpressionImpl(@NotNull ASTNode node) {
    super(node);
  }

  public GrArgumentList getArgumentList() {
    return findChildByClass(GrArgumentList.class);
  }

  public GrNamedArgument[] getNamedArguments() {
    GrArgumentList argList = getArgumentList();
    return argList != null ? argList.getNamedArguments() : GrNamedArgument.EMPTY_ARRAY;
  }

  public GrExpression[] getExpressionArguments() {
    GrArgumentList argList = getArgumentList();
    return argList != null ? argList.getExpressionArguments() : GrExpression.EMPTY_ARRAY;
  }

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

  public GrNamedArgument addNamedArgument(final GrNamedArgument namedArgument) throws IncorrectOperationException {
    GrArgumentList list = getArgumentList();
    assert list != null;
    if (list.getText().trim().length() == 0) {
      final GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(getProject());
      final GrArgumentList newList = factory.createExpressionArgumentList();
      list = (GrArgumentList)list.replace(newList);
    }
    return list.addNamedArgument(namedArgument);
  }
}
