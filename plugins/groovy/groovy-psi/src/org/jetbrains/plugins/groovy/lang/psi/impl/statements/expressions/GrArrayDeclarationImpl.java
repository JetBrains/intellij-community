// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

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
  public void accept(@NotNull GroovyElementVisitor visitor) {
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
