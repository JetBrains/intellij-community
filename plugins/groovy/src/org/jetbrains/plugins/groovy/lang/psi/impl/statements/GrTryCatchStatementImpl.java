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

package org.jetbrains.plugins.groovy.lang.psi.impl.statements;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrCatchClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrFinallyClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrTryCatchStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiElementImpl;

/**
 * @author ilyas
 */
public class GrTryCatchStatementImpl extends GroovyPsiElementImpl implements GrTryCatchStatement {
  public GrTryCatchStatementImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(GroovyElementVisitor visitor) {
    visitor.visitTryStatement(this);
  }

  public String toString() {
    return "Try statement";
  }

  public GrOpenBlock getTryBlock() {
    return findChildByClass(GrOpenBlock.class);
  }

  public GrCatchClause[] getCatchClauses() {
    return findChildrenByClass(GrCatchClause.class);
  }

  public GrFinallyClause getFinallyClause() {
    return findChildByClass(GrFinallyClause.class);
  }

  public GrCatchClause addCatchClause(@NotNull GrCatchClause clause, @Nullable GrCatchClause anchorBefore) {
    PsiElement anchor = anchorBefore;
    if (anchor == null) {
      anchor = getTryBlock();
    }
    return (GrCatchClause)addAfter(clause, anchor);
  }
}