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
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.templateLanguages.OuterLanguageElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.util.containers.CollectionFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.formatter.processors.GroovyIndentProcessor;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.GrQualifiedReference;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrThrowsClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrLabeledStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrCodeBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrBinaryExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrConditionalExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameterList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrAnonymousClassDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrExtendsClause;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

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
      return generateForBinaryExpr(node, myWrap, mySettings, block.myInnerAlignments);
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
      for (ASTNode childNode : children) {
        if (childNode.getTextRange().getLength() > 0) {
          final Indent indent = GroovyIndentProcessor.getChildIndent(block, childNode);
          subBlocks.add(new GroovyBlock(childNode, myAlignment, indent, myWrap, mySettings));
        }
      }
      return subBlocks;
    }

    // chained properties, calls, indexing, etc
    if (NESTED.contains(block.getNode().getElementType()) && blockPsi.getParent() != null && !NESTED.contains(blockPsi.getParent().getNode().getElementType())) {
      final List<Block> subBlocks = new ArrayList<Block>();
      Alignment dotsAlignment = mySettings.ALIGN_MULTILINE_CHAINED_METHODS ? Alignment.createAlignment() : null;
      addNestedChildren(node.getPsi(), subBlocks, dotsAlignment, myWrap, mySettings, true);
      return subBlocks;
    }

    // For Parameter lists
    if (isListLikeClause(blockPsi)) {
      final ArrayList<Block> subBlocks = new ArrayList<Block>();
      List<ASTNode> astNodes = visibleChildren(node);
      final Alignment alignment = mustAlign(blockPsi, mySettings, astNodes) ? Alignment.createAlignment() : null;
      for (ASTNode childNode : astNodes) {
        final Indent indent = GroovyIndentProcessor.getChildIndent(block, childNode);
        subBlocks.add(new GroovyBlock(childNode, isKeyword(childNode) ? null : alignment, indent, myWrap, mySettings));
      }
      return subBlocks;
    }

    if (blockPsi instanceof GrCodeBlock || blockPsi instanceof GroovyFile) {
      List<ASTNode> children = visibleChildren(node);
      Map<GrBinaryExpression, Alignment> innerAlignments = calculateInnerAlignments(children);
      final ArrayList<Block> subBlocks = new ArrayList<Block>();
      for (ASTNode childNode : children) {
        final Indent indent = GroovyIndentProcessor.getChildIndent(block, childNode);
        subBlocks.add(new GroovyBlock(childNode, null, indent, myWrap, mySettings, innerAlignments));
      }
      return subBlocks;
    }

    // For other cases
    final ArrayList<Block> subBlocks = new ArrayList<Block>();
    for (ASTNode childNode : visibleChildren(node)) {
      final Indent indent = GroovyIndentProcessor.getChildIndent(block, childNode);
      subBlocks.add(new GroovyBlock(childNode, blockPsi instanceof GrAnonymousClassDefinition ? null : myAlignment, indent, myWrap, mySettings, block.myInnerAlignments));
    }
    return subBlocks;
  }

  private static Map<GrBinaryExpression, Alignment> calculateInnerAlignments(List<ASTNode> children) {
    Map<GrBinaryExpression, Alignment> innerAlignments = CollectionFactory.hashMap();
    List<Alignment> currentGroup = null;
    for (ASTNode child : children) {
      PsiElement psi = child.getPsi();
      if (psi instanceof GrLabeledStatement) {
        List<GrBinaryExpression> table = getTable(((GrLabeledStatement)psi).getStatement());
        if (table.isEmpty()) {
          currentGroup = null;
        }
        else {
          currentGroup = new ArrayList<Alignment>();
          for (GrBinaryExpression expression : table) {
            Alignment alignment = Alignment.createAlignment(true);
            currentGroup.add(alignment);
            innerAlignments.put(expression, alignment);
          }
        }
      } else if (currentGroup != null && isTablePart(psi)) {
        List<GrBinaryExpression> table = getTable((GrStatement)psi);
        for (int i = 0; i < Math.min(table.size(), currentGroup.size()); i++) {
          innerAlignments.put(table.get(i), currentGroup.get(i));
        }
      } else {
        if (psi instanceof PsiComment) {
          PsiElement prev = psi.getPrevSibling();
          if (prev != null && prev.getNode().getElementType() != mNLS) {
            continue;
          }
        }
        currentGroup = null;
      }
    }
    return innerAlignments;
  }

  private static List<GrBinaryExpression> getTable(GrStatement statement) {
    LinkedList<GrBinaryExpression> result = new LinkedList<GrBinaryExpression>();
    while (isTablePart(statement)) {
      result.addFirst((GrBinaryExpression)statement);
      statement = ((GrBinaryExpression)statement).getLeftOperand();
    }
    return result;
  }

  private static boolean isTablePart(PsiElement psi) {
    return psi instanceof GrBinaryExpression && mBOR == ((GrBinaryExpression)psi).getOperationTokenType();
  }

  private static List<ASTNode> visibleChildren(ASTNode node) {
    ArrayList<ASTNode> list = new ArrayList<ASTNode>();
    for (ASTNode astNode : getGroovyChildren(node)) {
      if (canBeCorrectBlock(astNode)) {
        list.add(astNode);
      }
    }
    return list;
  }

  private static boolean mustAlign(PsiElement blockPsi, CodeStyleSettings mySettings, List<ASTNode> children) {
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
      return children.size() != 3 || children.get(0).getElementType() != mLPAREN
             || children.get(1).getElementType() != CLOSABLE_BLOCK || children.get(2).getElementType() != mRPAREN;
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
    return node != null && (TokenSets.KEYWORDS.contains(node.getElementType()) ||
        TokenSets.BRACES.contains(node.getElementType()));
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
  private static List<Block> generateForBinaryExpr(final ASTNode node, Wrap myWrap, CodeStyleSettings mySettings, Map<GrBinaryExpression, Alignment> inner) {
    final ArrayList<Block> subBlocks = new ArrayList<Block>();
    Alignment alignment = mySettings.ALIGN_MULTILINE_BINARY_OPERATION ? Alignment.createAlignment() : null;


    GrBinaryExpression binary = (GrBinaryExpression) node.getPsi();
    assert binary != null;
    addBinaryChildrenRecursively(binary, subBlocks, Indent.getContinuationWithoutFirstIndent(), alignment, myWrap, mySettings, inner);
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
                                                   Alignment alignment, Wrap myWrap, CodeStyleSettings mySettings, Map<GrBinaryExpression, Alignment> inner) {
    if (elem == null) return;
    // For binary expressions
    if ((elem instanceof GrBinaryExpression)) {
      GrBinaryExpression myExpr = ((GrBinaryExpression) elem);
      if (myExpr.getLeftOperand() instanceof GrBinaryExpression) {
        addBinaryChildrenRecursively(myExpr.getLeftOperand(), list, Indent.getContinuationWithoutFirstIndent(), alignment, myWrap, mySettings, inner);
      }
      PsiElement op = ((GrBinaryExpression)elem).getOperationToken();
      for (ASTNode childNode : visibleChildren(elem.getNode())) {
        PsiElement psi = childNode.getPsi();
        if (!(psi instanceof GrBinaryExpression)) {
          list.add(new GroovyBlock(childNode, op == psi ? inner.get(myExpr) : alignment, indent, myWrap, mySettings));
        }
      }
      if (myExpr.getRightOperand() instanceof GrBinaryExpression) {
        addBinaryChildrenRecursively(myExpr.getRightOperand(), list, Indent.getContinuationWithoutFirstIndent(), alignment, myWrap, mySettings, inner);
      }
    }
  }


  private static void addNestedChildren(final PsiElement elem, List<Block> list,
                                        @Nullable final Alignment alignment,
                                        final Wrap wrap,
                                        final CodeStyleSettings settings, final boolean topLevel) {
    final List<ASTNode> children = visibleChildren(elem.getNode());
    if (elem instanceof GrMethodCallExpression) {
      GrExpression invokedExpression = ((GrMethodCallExpression)elem).getInvokedExpression();
      if (invokedExpression instanceof GrQualifiedReference) {
        final PsiElement nameElement = ((GrQualifiedReference)invokedExpression).getReferenceNameElement();
        if (nameElement != null) {
          List<ASTNode> grandChildren = visibleChildren(invokedExpression.getNode());
          int i = 0;
          while (i < grandChildren.size() && nameElement != grandChildren.get(i).getPsi()) { i++; }
          if (i > 0) {
            processNestedChildrenPrefix(list, alignment, wrap, settings, false, grandChildren, i);
          }
          if (i < grandChildren.size()) {
            assert nameElement == grandChildren.get(i).getPsi();
            list.add(new MethodCallWithoutQualifierBlock(nameElement, null, wrap, settings, topLevel, children, elem));
          }
          return;
        }
      }

    }


    processNestedChildrenPrefix(list, alignment, wrap, settings, topLevel, children, children.size());
  }

  private static void processNestedChildrenPrefix(List<Block> list,
                                                  Alignment alignment,
                                                  Wrap wrap,
                                                  CodeStyleSettings settings,
                                                  boolean topLevel, List<ASTNode> children, int limit) {
    ASTNode fst = children.get(0);
    assert limit > 0;
    if (NESTED.contains(fst.getElementType())) {
      addNestedChildren(fst.getPsi(), list, alignment, wrap, settings, false);
    } else {
      list.add(new GroovyBlock(fst, null, Indent.getContinuationWithoutFirstIndent(), wrap, settings));
    }
    addNestedChildrenSuffix(list, alignment, wrap, settings, topLevel, children, limit);
  }

  static void addNestedChildrenSuffix(List<Block> list,
                                      Alignment alignment,
                                      Wrap wrap,
                                      CodeStyleSettings settings,
                                      boolean topLevel, List<ASTNode> children, int limit) {
    for (int i = 1; i < limit; i++) {
      ASTNode childNode = children.get(i);
      if (canBeCorrectBlock(childNode)) {
        IElementType type = childNode.getElementType();
        Indent indent = topLevel || NESTED.contains(type) || type == mIDENT ? Indent.getContinuationWithoutFirstIndent() : Indent.getNoneIndent();
        list.add(new GroovyBlock(childNode, TokenSets.DOTS.contains(type) ? alignment : null, indent, wrap, settings));
      }
    }
  }
}
