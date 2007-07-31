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

package org.jetbrains.plugins.groovy.lang.psi.impl.statements.clauses;

import com.intellij.lang.ASTNode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrCondition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.clauses.GrTraditionalForClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiElementImpl;

import java.util.List;
import java.util.ArrayList;

/**
 * @author ilyas
 */
public class GrTraditionalForClauseImpl extends GroovyPsiElementImpl implements GrTraditionalForClause {
  public GrTraditionalForClauseImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(GroovyElementVisitor visitor) {
    visitor.visitForClause(this);
  }

  public String toString() {
    return "Traditional FOR clause";
  }

  public GrVariable[] getDeclaredVariables() {
    GrVariableDeclaration declaration = findChildByClass(GrVariableDeclaration.class);
    if (declaration == null) return GrVariable.EMPTY_ARRAY;
    return declaration.getVariables();
  }

  public GrCondition[] getInitialization() {
    List<GrCondition> result = new ArrayList<GrCondition>();
    final ASTNode first = getFirstSemicolon();
    for (ASTNode child = getNode().getFirstChildNode(); child != null && child != first; child = child.getTreeNext()) {
      if (child.getPsi() instanceof GrCondition) {
        result.add((GrCondition) child.getPsi());
      }
    }
    return result.toArray(new GrCondition[result.size()]);
  }

  public GrExpression getCondition() {
    final ASTNode first = getFirstSemicolon();
    if (first == null) return null;
    for (ASTNode child = first.getTreeNext(); child != null; child = child.getTreeNext()) {
      if (child.getPsi() instanceof GrExpression) {
        return (GrExpression) child.getPsi();
      }
    }

    return null;
  }

  public GrExpression[] getUpdate() {
    final ASTNode second = getSecondSemicolon();
    if (second == null) return GrExpression.EMPTY_ARRAY;

    List<GrExpression> result = new ArrayList<GrExpression>();

    for (ASTNode child = second; child != null; child = child.getTreeNext()) {
      if (child.getPsi() instanceof GrExpression) {
        result.add((GrExpression) child.getPsi());
      }
    }
    return result.toArray(new GrExpression[result.size()]);
  }

  private ASTNode getFirstSemicolon() {
    for (ASTNode child = getNode().getFirstChildNode(); child != null; child = child.getTreeNext()) {
      if (child.getElementType() == GroovyElementTypes.mSEMI) {
        return child;
      }
    }

    return null;
  }

  private ASTNode getSecondSemicolon() {
    boolean firstPassed = false;
    for (ASTNode child = getNode().getFirstChildNode(); child != null; child = child.getTreeNext()) {
      if (child.getElementType() == GroovyElementTypes.mSEMI) {
        if (firstPassed) {
          return child;
        } else {
          firstPassed = true;
        }
      }
    }
    return null;
  }
}