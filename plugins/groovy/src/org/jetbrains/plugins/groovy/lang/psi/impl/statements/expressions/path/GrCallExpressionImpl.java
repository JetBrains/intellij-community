/*
 * Copyright 2000-2007 JetBrains s.r.o.
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

import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.GrExpressionImpl;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.annotations.NotNull;
import com.intellij.lang.ASTNode;

/**
 * @author ven
 */
public abstract class GrCallExpressionImpl extends GrExpressionImpl {
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
}
