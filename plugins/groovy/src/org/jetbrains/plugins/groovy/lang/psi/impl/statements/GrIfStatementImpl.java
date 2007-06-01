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

package org.jetbrains.plugins.groovy.lang.psi.impl.statements;

import com.intellij.lang.ASTNode;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrCondition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrIfStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiElementImpl;

/**
 * @autor: ilyas
 */
public class GrIfStatementImpl extends GroovyPsiElementImpl implements GrIfStatement {
  public GrIfStatementImpl(@NotNull ASTNode node) {
    super(node);
  }

  public String toString() {
    return "IF statement";
  }

  public GrCondition getCondition() {
    GroovyPsiElement condition = findChildByClass(GrCondition.class);
    if (condition != null) {
      return (GrCondition) condition;
    }
    return null;
  }

  public GrCondition getThenBranch() {
    GrCondition[] statements = findChildrenByClass(GrCondition.class);
    if (statements.length > 1 && (statements[1] instanceof GrStatement)) {
      return statements[1];
    }
    if (statements.length > 1 && (statements[1] instanceof GrOpenBlock)) {
      return statements[1];
    }
    return null;
  }

  public GrCondition getElseBranch() {
    GrCondition[] statements = findChildrenByClass(GrCondition.class);
    if (statements.length == 3 && (statements[2] instanceof GrStatement)) {
      return statements[2];
    }
    if (statements.length == 3 && (statements[2] instanceof GrOpenBlock)) {
      return statements[2];
    }
    return null;
  }

  public GrCondition replaceThenBranch(GrCondition newBranch) throws IncorrectOperationException {
    if (getThenBranch() == null ||
        newBranch == null) {
      throw new IncorrectOperationException();
    }
    ASTNode oldBodyNode = getThenBranch().getNode();
    this.getNode().replaceChild(oldBodyNode, newBranch.getNode());
    ASTNode newNode = newBranch.getNode();
    if (!(newNode.getPsi() instanceof GrCondition)) {
      throw new IncorrectOperationException();
    }
    return (GrCondition) newNode.getPsi();
  }

  public GrCondition replaceElseBranch(GrCondition newBranch) throws IncorrectOperationException {
    if (getElseBranch() == null ||
        newBranch == null) {
      throw new IncorrectOperationException();
    }
    ASTNode oldBodyNode = getElseBranch().getNode();
    this.getNode().replaceChild(oldBodyNode, newBranch.getNode());
    ASTNode newNode = newBranch.getNode();
    if (!(newNode.getPsi() instanceof GrCondition)) {
      throw new IncorrectOperationException();
    }
    return (GrCondition) newNode.getPsi();
  }

}

