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

package org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions;

import com.intellij.lang.ASTNode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrArrayDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiElementImpl;

/**
 * @author ilyas
 */
public class GrArrayDeclarationImpl extends GroovyPsiElementImpl implements GrArrayDeclaration {
  public GrArrayDeclarationImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  public void accept(GroovyElementVisitor visitor) {
    visitor.visitArrayDeclaration(this);
  }

  public String toString() {
    return "Array declaration";
  }

  @Override
  public GrExpression[] getBoundExpressions() {
    return findChildrenByClass(GrExpression.class);
  }

  @Override
  public int getArrayCount() {
    final ASTNode node = getNode();
    int num = 0;
    ASTNode run = node.getFirstChildNode();
    while (run != null) {
      if (run.getElementType() == GroovyTokenTypes.mLBRACK) num++;
      run = run.getTreeNext();
    }

    return num;
  }
}
