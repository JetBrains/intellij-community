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
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.PsiComment;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrTryCatchStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrCondition;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiElementImpl;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;

import java.util.ArrayList;

/**
 * @author ilyas
 */
public class GrTryCatchStmtImpl extends GroovyPsiElementImpl implements GrTryCatchStatement {
  public GrTryCatchStmtImpl(@NotNull ASTNode node) {
    super(node);
  }

  public String toString() {
    return "Try statement";
  }

  public GrOpenBlock getTryBlock() {
    return findChildByClass(GrOpenBlock.class);

  }

  public GrOpenBlock[] getCatchBlocks() {
    GrOpenBlock[] blocks = findChildrenByClass(GrOpenBlock.class);
    if (blocks.length == 1) {
      return new GrOpenBlock[0];
    }
    ArrayList<GrOpenBlock> blockList = new ArrayList<GrOpenBlock>();
    for (GrOpenBlock block : blocks) {
      if (!block.equals(getTryBlock()) &&
          !block.equals(getFinallyBlock())){
        blockList.add(block);
      }
    }
    return blockList.toArray(new GrOpenBlock[0]);
  }

  public GrOpenBlock getFinallyBlock() {
    GrOpenBlock[] blocks = findChildrenByClass(GrOpenBlock.class);
    if (blocks.length > 1) {
      GrOpenBlock candidate = blocks[blocks.length-1];
      PsiElement pred = candidate.getPrevSibling();
      while (pred != null &&
          (pred instanceof PsiWhiteSpace ||
          pred instanceof PsiComment ||
              GroovyTokenTypes.mNLS.equals(pred.getNode().getElementType()))){
        pred = pred.getPrevSibling();
      }
      if (pred != null &&
          GroovyTokenTypes.kFINALLY.equals(pred.getNode().getElementType())) {
        return candidate;
      }
    }

    return null;
  }
}