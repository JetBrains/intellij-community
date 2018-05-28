// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.impl.statements;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrWhileStatement;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrWhileStatementBase;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

public class GrWhileStatementImpl extends GrWhileStatementBase implements GrWhileStatement {

  public GrWhileStatementImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  public void accept(GroovyElementVisitor visitor) {
    visitor.visitWhileStatement(this);
  }

  public String toString() {
    return "WHILE statement";
  }

  @Override
  @Nullable
  public GrStatement getBody() {
    PsiElement rParenth = getRParenth();
    if (rParenth == null) return null;

    PsiElement afterRParenth = PsiUtil.skipWhitespacesAndComments(rParenth.getNextSibling(), true);
    return afterRParenth instanceof GrStatement ? (GrStatement)afterRParenth : null;
  }
}
