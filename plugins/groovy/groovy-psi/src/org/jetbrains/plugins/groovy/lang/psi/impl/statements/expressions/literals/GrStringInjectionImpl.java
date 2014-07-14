/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

package org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.literals;

import com.intellij.lang.ASTNode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrStringInjection;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiElementImpl;

/**
 * @author Maxim.Medvedev
 */
public class GrStringInjectionImpl extends GroovyPsiElementImpl implements GrStringInjection {
  public GrStringInjectionImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  @Nullable
  public GrExpression getExpression() {
    final GrExpression expression = findExpressionChild(this);
    return expression instanceof GrClosableBlock ? null : expression;
  }

  @Override
  @Nullable
  public GrClosableBlock getClosableBlock() {
    return findChildByClass(GrClosableBlock.class);
  }

  @Override
  public String toString() {
    return "GString injection";
  }

  @Override
  public void accept(GroovyElementVisitor visitor) {
    visitor.visitGStringInjection(this);
  }
}
