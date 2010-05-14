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

package org.jetbrains.plugins.groovy.formatter;

import com.intellij.formatting.Alignment;
import com.intellij.formatting.Block;
import com.intellij.formatting.Indent;
import com.intellij.formatting.Wrap;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.templateLanguages.OuterLanguageElement;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.formatter.processors.GroovyIndentProcessor;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrThrowsClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrCodeBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrBinaryExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrConditionalExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameterList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrExtendsClause;

import java.util.ArrayList;
import java.util.List;

/**
 * Utility class to generate myBlock hierarchy
 *
 * @author ilyas
 */
public class GroovyBlockGenerator implements GroovyElementTypes {

  private static final TokenSet NESTED = TokenSet.create(REFERENCE_EXPRESSION,
      PATH_INDEX_PROPERTY,
      PATH_METHOD_CALL,
      PATH_PROPERTY_REFERENCE);


  public static List<Block> generateSubBlocks(ASTNode node,
                                              Alignment myAlignment,
                                              Wrap myWrap,
                                              CodeStyleSettings mySettings,
                                              GroovyBlock block) {
    //For binary expressions
    PsiElement blockPsi = block.getNode().getPsi();
    if (blockPsi instanceof GrBinaryExpression &&
        !(blockPsi.getParent() instanceof GrBinaryExpression)) {
      return generateForBinaryExpr(node, myWrap, mySettings);
    }

    //For multiline strings
    if ((block.getNode().getElementType() == mSTRING_LITERAL ||
        block.getNode().getElementType() == mGSTRING_LITERAL) &&
        block.getTextRange().equals(block.getNode().getTextRange())) {
      String text = block.getNode().getText();
      if (text.length() > 6) {
        if (text.substring(0, 3).equals("'''") && text.substring(text.length() - 3).equals("'''") ||
            text.substring(0, 3).equals("\"\"\"") & text.substring(text.length() - 3).equals("\"\"\"")) {
          return generateForMultiLineString(block.getNode(), myAlignment, myWrap, mySettings);
        }
      }
    }

    if (block.getNode().getElementType() == mGSTRING_BEGIN &&
        block.getTextRange().equals(block.getNode().getTextRange())) {
      String text = block.getNode().getText();
      if (text.length() > 3) {
        if (text.substring(0, 3).equals("\"\"\"")) {
          return generateForMultiLineGStringBegin(block.getNode(), myAlignment, myWrap, mySettings);
        }
      }

    }

    //for gstrings
    if (block.getNode().getElementType() == GSTRING) {
      final ArrayList<Block> subBlocks = new ArrayList<Block>();
      ASTNode[] children = getGroovyChildren(node);
      ASTNode prevChildNode = null;
      for (ASTNode childNode : children) {
        if (childNode.getTextRange().getLength() > 0) {
          final Indent indent = GroovyIndentProcessor.getChildIndent(block, prevChildNode, childNode);
          subBlocks.add(new GroovyBlock(childNode, myAlignment, indent, myWrap, mySettings));
        }
        prevChildNode = childNode;
      }
      return subBlocks;
    }

    //For nested selections
    if (NESTED.contains(block.getNode().getElementType()) &&
        blockPsi.getParent() != null &&
        blockPsi.getParent().getNode() != null &&
        !NESTED.contains(blockPsi.getParent().getNode().getElementType())) {
      return generateForNestedExpr(node, myAlignment, myWrap, mySettings);
    }

    // For Parameter lists
    if (isListLikeClause(blockPsi)) {
      final ArrayList<Block> subBlocks = new ArrayList<Block>();
      ASTNode[] children = node.getChildren(null);
      ASTNode prevChildNode = null;
      final Alignment alignment = mustAlign(blockPsi, mySettings, children) ? Alignment.createAlignment() : null;
      for (ASTNode childNode : children) {
        if (canBeCorrectBlock(childNode)) {
          final Indent indent = GroovyIndentProcessor.getChildIndent(block, prevChildNode, childNode);
          subBlocks.add(new GroovyBlock(childNode, isKeyword(childNode) ? null : alignment, indent, myWrap, mySettings));
          prevChildNode = childNode;
        }
      }
      return subBlocks;
    }

    // For other cases
    final ArrayList<Block> subBlocks = new ArrayList<Block>();
    ASTNode[] children = getGroovyChildren(node);
    ASTNode prevChildNode = null;
    for (ASTNode childNode : children) {
      if (canBeCorrectBlock(childNode)) {
        final Indent indent = GroovyIndentProcessor.getChildIndent(block, prevChildNode, childNode);
        subBlocks.add(new GroovyBlock(childNode, blockPsi instanceof GrCodeBlock ? null : myAlignment, indent, myWrap, mySettings));
        prevChildNode = childNode;
      }
    }
    return subBlocks;
  }

  private static boolean mustAlign(PsiElement blockPsi, CodeStyleSettings mySettings, ASTNode[] children) {
    // We don't want to align single call argument if it's a closure. The reason is that it looks better to have call like
    //
    // foo({
    //   println 'xxx'
    // })
    //
    // than
    //
    // foo({
    //       println 'xxx'
    //     })
    if (blockPsi instanceof GrArgumentList && mySettings.ALIGN_MULTILINE_PARAMETERS_IN_CALLS) {
      List<ASTNode> nonWhiteSpaceNodes = new ArrayList<ASTNode>();
      for (ASTNode child : children) {
        if (!WHITE_SPACES_OR_COMMENTS.contains(child.getElementType())) {
          nonWhiteSpaceNodes.add(child);
        }
      }
      return nonWhiteSpaceNodes.size() != 3 || nonWhiteSpaceNodes.get(0).getElementType() != mLPAREN 
             || nonWhiteSpaceNodes.get(1).getElementType() != CLOSABLE_BLOCK || nonWhiteSpaceNodes.get(2).getElementType() != mRPAREN;
    }

    return blockPsi instanceof GrParameterList && mySettings.ALIGN_MULTILINE_PARAMETERS ||
        blockPsi instanceof GrExtendsClause && mySettings.ALIGN_MULTILINE_EXTENDS_LIST ||
        blockPsi instanceof GrThrowsClause && mySettings.ALIGN_MULTILINE_THROWS_LIST ||
        blockPsi instanceof GrConditionalExpression && mySettings.ALIGN_MULTILINE_TERNARY_OPERATION;
  }

  private static boolean isListLikeClause(PsiElement blockPsi) {
    return blockPsi instanceof GrParameterList ||
        blockPsi instanceof GrArgumentList ||
        blockPsi instanceof GrConditionalExpression ||
        blockPsi instanceof GrExtendsClause ||
        blockPsi instanceof GrThrowsClause;
  }

  private static boolean isKeyword(ASTNode node) {
    return node != null && (GroovyTokenTypes.KEYWORDS.contains(node.getElementType()) ||
        GroovyTokenTypes.BRACES.contains(node.getElementType()));
  }


  private static List<Block> generateForMultiLineString(ASTNode node, Alignment myAlignment, Wrap myWrap, CodeStyleSettings mySettings) {
    final ArrayList<Block> subBlocks = new ArrayList<Block>();
    final int start = node.getTextRange().getStartOffset();
    final int end = node.getTextRange().getEndOffset();

    subBlocks.add(new GroovyBlock(node, myAlignment, Indent.getNoneIndent(), myWrap, mySettings) {
      @NotNull
      public TextRange getTextRange() {
        return new TextRange(start, start + 3);
      }
    });
    subBlocks.add(new GroovyBlock(node, myAlignment, Indent.getAbsoluteNoneIndent(), myWrap, mySettings) {
      @NotNull
      public TextRange getTextRange() {
        return new TextRange(start + 3, end - 3);
      }
    });
    subBlocks.add(new GroovyBlock(node, myAlignment, Indent.getAbsoluteNoneIndent(), myWrap, mySettings) {
      @NotNull
      public TextRange getTextRange() {
        return new TextRange(end - 3, end);
      }
    });
    return subBlocks;
  }

  private static List<Block> generateForMultiLineGStringBegin(ASTNode node, Alignment myAlignment, Wrap myWrap, CodeStyleSettings mySettings) {
    final ArrayList<Block> subBlocks = new ArrayList<Block>();
    final int start = node.getTextRange().getStartOffset();
    final int end = node.getTextRange().getEndOffset();

    subBlocks.add(new GroovyBlock(node, myAlignment, Indent.getNoneIndent(), myWrap, mySettings) {
      @NotNull
      public TextRange getTextRange() {
        return new TextRange(start, start + 3);
      }
    });
    subBlocks.add(new GroovyBlock(node, myAlignment, Indent.getAbsoluteNoneIndent(), myWrap, mySettings) {
      @NotNull
      public TextRange getTextRange() {
        return new TextRange(start + 3, end);
      }
    });
    return subBlocks;
  }

  /**
   * @param node Tree node
   * @return true, if the current node can be myBlock node, else otherwise
   */
  private static boolean canBeCorrectBlock(final ASTNode node) {
    return (node.getText().trim().length() > 0);
  }


  private static ASTNode[] getGroovyChildren(final ASTNode node) {
    PsiElement psi = node.getPsi();
    if (psi instanceof OuterLanguageElement) {
      TextRange range = node.getTextRange();
      ArrayList<ASTNode> childList = new ArrayList<ASTNode>();
      PsiFile groovyFile = psi.getContainingFile().getViewProvider().getPsi(GroovyFileType.GROOVY_LANGUAGE);
      if (groovyFile instanceof GroovyFileBase) {
        addChildNodes(groovyFile, childList, range);
      }
      return childList.toArray(new ASTNode[childList.size()]);
    }
    return node.getChildren(null);
  }

  private static void addChildNodes(PsiElement elem, ArrayList<ASTNode> childNodes, TextRange range) {
    ASTNode node = elem.getNode();
    if (range.contains(elem.getTextRange()) && node != null) {
      childNodes.add(node);
    } else {
      for (PsiElement child : elem.getChildren()) {
        addChildNodes(child, childNodes, range);
      }
    }

  }

  /**
   * Generates blocks for binary expressions
   *
   * @return
   * @param node
   */
  private static List<Block> generateForBinaryExpr(final ASTNode node, Wrap myWrap, CodeStyleSettings mySettings) {
    final ArrayList<Block> subBlocks = new ArrayList<Block>();
    Alignment alignment = mySettings.ALIGN_MULTILINE_BINARY_OPERATION ? Alignment.createAlignment() : null;
    GrBinaryExpression myExpr = (GrBinaryExpression) node.getPsi();
    ASTNode[] children = node.getChildren(null);
    if (myExpr.getLeftOperand() instanceof GrBinaryExpression) {
      addBinaryChildrenRecursively(myExpr.getLeftOperand(), subBlocks, Indent.getContinuationWithoutFirstIndent(), alignment, myWrap, mySettings);
    }
    for (ASTNode childNode : children) {
      if (canBeCorrectBlock(childNode) &&
          !(childNode.getPsi() instanceof GrBinaryExpression)) {
        subBlocks.add(new GroovyBlock(childNode, alignment, Indent.getContinuationWithoutFirstIndent(), myWrap, mySettings));
      }
    }
    if (myExpr.getRightOperand() instanceof GrBinaryExpression) {
      addBinaryChildrenRecursively(myExpr.getRightOperand(), subBlocks, Indent.getContinuationWithoutFirstIndent(), alignment, myWrap, mySettings);
    }
    return subBlocks;
  }

  /**
   * Adds all children of specified element to given list
   *
   * @param elem
   * @param list
   * @param indent
   * @param alignment
   */
  private static void addBinaryChildrenRecursively(PsiElement elem,
                                                   List<Block> list,
                                                   Indent indent,
                                                   Alignment alignment, Wrap myWrap, CodeStyleSettings mySettings) {
    if (elem == null) return;
    ASTNode[] children = elem.getNode().getChildren(null);
    // For binary expressions
    if ((elem instanceof GrBinaryExpression)) {
      GrBinaryExpression myExpr = ((GrBinaryExpression) elem);
      if (myExpr.getLeftOperand() instanceof GrBinaryExpression) {
        addBinaryChildrenRecursively(myExpr.getLeftOperand(), list, Indent.getContinuationWithoutFirstIndent(), alignment, myWrap, mySettings);
      }
      for (ASTNode childNode : children) {
        if (canBeCorrectBlock(childNode) &&
            !(childNode.getPsi() instanceof GrBinaryExpression)) {
          list.add(new GroovyBlock(childNode, alignment, indent, myWrap, mySettings));
        }
      }
      if (myExpr.getRightOperand() instanceof GrBinaryExpression) {
        addBinaryChildrenRecursively(myExpr.getRightOperand(), list, Indent.getContinuationWithoutFirstIndent(), alignment, myWrap, mySettings);
      }
    }
  }


  /**
   * Generates blocks for nested expressions like a.b.c etc.
   *
   * @return
   * @param node
   */
  private static List<Block> generateForNestedExpr(final ASTNode node, Alignment myAlignment, Wrap myWrap, CodeStyleSettings mySettings) {
    final ArrayList<Block> subBlocks = new ArrayList<Block>();
    ASTNode children[] = node.getChildren(null);
    if (children.length > 0 && NESTED.contains(children[0].getElementType())) {
      addNestedChildrenRecursively(children[0].getPsi(), subBlocks, myAlignment, myWrap, mySettings);
    } else if (canBeCorrectBlock(children[0])) {
      subBlocks.add(new GroovyBlock(children[0], myAlignment, Indent.getContinuationWithoutFirstIndent(), myWrap, mySettings));
    }
    if (children.length > 1) {
      for (ASTNode childNode : children) {
        if (canBeCorrectBlock(childNode) &&
            children[0] != childNode) {
          subBlocks.add(new GroovyBlock(childNode, myAlignment, Indent.getContinuationWithoutFirstIndent(), myWrap, mySettings));
        }
      }
    }
    return subBlocks;
  }

  /**
   * Adds nested children for paths
   *
   * @param elem
   * @param list
   */
  private static void addNestedChildrenRecursively(PsiElement elem,
                                                   List<Block> list, Alignment myAlignment, Wrap myWrap, CodeStyleSettings mySettings) {
    ASTNode[] children = elem.getNode().getChildren(null);
    // For path expressions
    if (children.length > 0 && NESTED.contains(children[0].getElementType())) {
      addNestedChildrenRecursively(children[0].getPsi(), list, myAlignment, myWrap, mySettings);
    } else if (canBeCorrectBlock(children[0])) {
      list.add(new GroovyBlock(children[0], myAlignment, Indent.getContinuationWithoutFirstIndent(), myWrap, mySettings));
    }
    if (children.length > 1) {
      for (ASTNode childNode : children) {
        if (canBeCorrectBlock(childNode) &&
            children[0] != childNode) {
          if (elem.getNode() != null &&
              NESTED.contains(elem.getNode().getElementType())) {
            list.add(new GroovyBlock(childNode, myAlignment, Indent.getContinuationWithoutFirstIndent(), myWrap, mySettings));
          } else {
            list.add(new GroovyBlock(childNode, myAlignment, Indent.getNoneIndent(), myWrap, mySettings));
          }
        }
      }
    }
  }

}
