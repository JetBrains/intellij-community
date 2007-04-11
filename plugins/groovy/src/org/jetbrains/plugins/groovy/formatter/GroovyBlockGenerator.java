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

package org.jetbrains.plugins.groovy.formatter;

import com.intellij.formatting.Block;
import com.intellij.formatting.Indent;
import com.intellij.formatting.Alignment;
import com.intellij.formatting.Wrap;
import com.intellij.lang.ASTNode;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.PsiElement;

import java.util.List;
import java.util.ArrayList;

import org.jetbrains.plugins.groovy.formatter.processors.GroovyIndentProcessor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.clauses.GrCaseBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.clauses.GrCaseLabel;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.annotations.NotNull;

/**
 * Utility class to generate myBlock hierarchy
 *
 * @author Ilya.Sergey
 */
public class GroovyBlockGenerator implements GroovyElementTypes {

  private static ASTNode myNode;
  private static Alignment myAlignment;
  private static Wrap myWrap;
  private static CodeStyleSettings mySettings;
  private static GroovyBlock myBlock;


  public static List<Block> generateSubBlocks(ASTNode _myNode,
                                              Alignment _myAlignment,
                                              Wrap _myWrap,
                                              CodeStyleSettings _mySettings,
                                              GroovyBlock _block) {
    myNode = _myNode;
    myWrap = _myWrap;
    mySettings = _mySettings;
    myAlignment = _myAlignment;
    myBlock = _block;

    // For case block
    if (myBlock.getNode().getPsi() instanceof GrCaseBlock) {
      return generateForCaseBlock();
    }
    // For case labels
    if (myBlock.getNode().getPsi() instanceof GrCaseLabel &&
            (myBlock instanceof LargeGroovyBlock)) {
      return generateForCaseLabel();
    }

    // For other cases
    final ArrayList<Block> subBlocks = new ArrayList<Block>();
    ASTNode children[] = myNode.getChildren(null);
    int childNumber = children.length;
    if (childNumber == 0) {
      return subBlocks;
    }
    ASTNode prevChildNode = null;
    for (ASTNode childNode : children) {
      if (canBeCorrectBlock(childNode)) {
        final Indent indent = GroovyIndentProcessor.getChildIndent(myBlock, prevChildNode, childNode);
        subBlocks.add(new GroovyBlock(childNode, myAlignment, indent, myWrap, mySettings));
        prevChildNode = childNode;
      }
    }
    return subBlocks;
  }

  /**
   * Generates blocks for case block
   *
   * @return
   */
  private static List<Block> generateForCaseBlock() {
    final ArrayList<Block> subBlocks = new ArrayList<Block>();
    ASTNode children[] = myNode.getChildren(null);
    int childNumber = children.length;
    if (childNumber == 0) {
      return subBlocks;
    }
    ASTNode prevChildNode = null;
    for (ASTNode childNode : children) {
      if (canBeCorrectBlock(childNode) &&
              !(childNode.getPsi() instanceof GrStatement)) {
        final Indent indent = GroovyIndentProcessor.getChildIndent(myBlock, prevChildNode, childNode);
        if (!(childNode.getPsi() instanceof GrCaseLabel)) {
          subBlocks.add(new GroovyBlock(childNode, myAlignment, indent, myWrap, mySettings));
        } else {
          subBlocks.add(new LargeGroovyBlock(childNode, myAlignment, indent, myWrap, mySettings));
        }
        prevChildNode = childNode;
      }
    }
    return subBlocks;
  }

  /**
   * Generates blocks for case labels
   *
   * @return
   */
  private static List<Block> generateForCaseLabel() {
    final ArrayList<Block> subBlocks = new ArrayList<Block>();
    ASTNode prevChildNode = null;
    Indent oldIndent = GroovyIndentProcessor.getChildIndent(myBlock, prevChildNode, myNode);
    subBlocks.add(new GroovyBlock(myNode, myAlignment, oldIndent, myWrap, mySettings));

    PsiElement nextSibling = myNode.getPsi().getNextSibling();
    while (!(nextSibling == null) &&
            !(mRCURLY.equals(nextSibling.getNode().getElementType())) &&
            !(nextSibling instanceof GrCaseLabel)) {
      ASTNode childNode = nextSibling.getNode();
      if (canBeCorrectBlock(childNode)) {
        final Indent indent = GroovyIndentProcessor.getChildIndent(myBlock, prevChildNode, childNode);
        subBlocks.add(new GroovyBlock(childNode, myAlignment, indent, myWrap, mySettings));
        prevChildNode = childNode;
      }
      nextSibling = nextSibling.getNextSibling();
    }
    return subBlocks;
  }


  /**
   * @param node Tree node
   * @return true, if the current node can be myBlock node, else otherwise
   */
  private static boolean canBeCorrectBlock(final ASTNode node) {
    return (node.getText().trim().length() > 0);
  }


}
