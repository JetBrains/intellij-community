/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.intellij.psi.templateLanguages.OuterLanguageElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.formatter.processors.GroovyIndentProcessor;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.GrQualifiedReference;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrThrowsClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrLabeledStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrCodeBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrBinaryExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrConditionalExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameterList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrExtendsClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinitionBody;

import java.util.*;

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

  private static final Logger LOG = Logger.getInstance(GroovyBlockGenerator.class);

  private final GroovyBlock myBlock;
  private final ASTNode myNode;
  private final Alignment myAlignment;
  private final Wrap myWrap;
  private final CommonCodeStyleSettings mySettings;
  private final Map<PsiElement,Alignment> myInnerAlignments;
  private final GroovyCodeStyleSettings myGroovySettings;

  public GroovyBlockGenerator(GroovyBlock block) {
    this.myBlock = block;
    myNode = myBlock.getNode();
    myAlignment = myBlock.getAlignment();
    myWrap = myBlock.getWrap();
    mySettings = myBlock.getSettings();
    myInnerAlignments = myBlock.getInnerAlignments();
    myGroovySettings = myBlock.getGroovySettings();
  }


  public List<Block> generateSubBlocks() {

    //For binary expressions
    PsiElement blockPsi = myNode.getPsi();

    if (blockPsi instanceof GrBinaryExpression && !(blockPsi.getParent() instanceof GrBinaryExpression)) {
      return generateForBinaryExpr();
    }

    //For multiline strings
    if ((myNode.getElementType() == mSTRING_LITERAL || myNode.getElementType() == mGSTRING_LITERAL) &&
        myBlock.getTextRange().equals(myNode.getTextRange())) {
      String text = myNode.getText();
      if (text.length() > 6) {
        if (text.substring(0, 3).equals("'''") && text.substring(text.length() - 3).equals("'''") ||
            text.substring(0, 3).equals("\"\"\"") & text.substring(text.length() - 3).equals("\"\"\"")) {
          return generateForMultiLineString();
        }
      }
    }

    if (myNode.getElementType() == mGSTRING_BEGIN &&
        myBlock.getTextRange().equals(myNode.getTextRange())) {
      String text = myNode.getText();
      if (text.length() > 3) {
        if (text.substring(0, 3).equals("\"\"\"")) {
          return generateForMultiLineGStringBegin();
        }
      }

    }

    //for gstrings
    if (myNode.getElementType() == GSTRING) {
      final ArrayList<Block> subBlocks = new ArrayList<Block>();
      ASTNode[] children = getGroovyChildren(myNode);
      for (ASTNode childNode : children) {
        if (childNode.getTextRange().getLength() > 0) {
          final Indent indent = GroovyIndentProcessor.getChildIndent(myBlock, childNode);
          subBlocks.add(new GroovyBlock(childNode, myAlignment, indent, myWrap, mySettings, myGroovySettings, myInnerAlignments));
        }
      }
      return subBlocks;
    }

    // chained properties, calls, indexing, etc
    if (NESTED.contains(myNode.getElementType()) && blockPsi.getParent() != null && !NESTED.contains(blockPsi.getParent().getNode().getElementType())) {
      final List<Block> subBlocks = new ArrayList<Block>();
      Alignment dotsAlignment = mySettings.ALIGN_MULTILINE_CHAINED_METHODS ? Alignment.createAlignment() : null;
      addNestedChildren(myNode.getPsi(), subBlocks, dotsAlignment, true);
      return subBlocks;
    }

    // For Parameter lists
    if (isListLikeClause(blockPsi)) {
      final ArrayList<Block> subBlocks = new ArrayList<Block>();
      List<ASTNode> astNodes = visibleChildren(myNode);
      final Alignment newAlignment = mustAlign(blockPsi, astNodes) ? Alignment.createAlignment() : null;
      for (ASTNode childNode : astNodes) {
        final Indent indent = GroovyIndentProcessor.getChildIndent(myBlock, childNode);
        subBlocks.add(new GroovyBlock(childNode, isKeyword(childNode) ? null : newAlignment, indent, myWrap, mySettings, myGroovySettings,
                                      myInnerAlignments));
      }
      return subBlocks;
    }

    boolean classLevel = blockPsi instanceof GrTypeDefinitionBody;
    if (blockPsi instanceof GrCodeBlock || blockPsi instanceof GroovyFile || classLevel) {
      List<ASTNode> children = visibleChildren(myNode);
      calculateAlignments(children, classLevel);
      final ArrayList<Block> subBlocks = new ArrayList<Block>();
      for (ASTNode childNode : children) {
        final Indent indent = GroovyIndentProcessor.getChildIndent(myBlock, childNode);
        Alignment alignmentToUse = classLevel ? myAlignment : myInnerAlignments.get(childNode.getPsi());
        subBlocks.add(new GroovyBlock(childNode, alignmentToUse, indent, myWrap, mySettings, myGroovySettings, myInnerAlignments));
      }
      return subBlocks;
    }

    // For other cases
    final ArrayList<Block> subBlocks = new ArrayList<Block>();
    for (ASTNode childNode : visibleChildren(myNode)) {
      final Indent indent = GroovyIndentProcessor.getChildIndent(myBlock, childNode);
      subBlocks.add(new GroovyBlock(childNode, myInnerAlignments.get(childNode.getPsi()), indent, myWrap, mySettings, myGroovySettings,
                                    myInnerAlignments));
    }
    return subBlocks;
  }

  private void calculateAlignments(List<ASTNode> children, boolean classLevel) {
    List<Alignment> currentGroup = null;
    for (ASTNode child : children) {
      PsiElement psi = child.getPsi();
      if (psi instanceof GrLabeledStatement) {
        List<LeafPsiElement> table = getSpockTable(((GrLabeledStatement)psi).getStatement());
        if (table.isEmpty()) {
          currentGroup = null;
        }
        else {
          currentGroup = new ArrayList<Alignment>();
          for (LeafPsiElement expression : table) {
            Alignment alignment = Alignment.createAlignment(true);
            currentGroup.add(alignment);
            ContainerUtil.putIfNotNull(expression, alignment, myInnerAlignments);
          }
        }
      } else if (currentGroup != null && isTablePart(psi)) {
        List<LeafPsiElement> table = getSpockTable((GrStatement)psi);
        for (int i = 0; i < Math.min(table.size(), currentGroup.size()); i++) {
          myInnerAlignments.put(table.get(i), currentGroup.get(i));
        }
      } else if (psi instanceof GrVariableDeclaration) {
        if (!classLevel || currentGroup == null || fieldGroupEnded(psi)) {
          currentGroup = Arrays.asList(Alignment.createAlignment(true), Alignment.createAlignment(true), Alignment.createAlignment(true));
        }

        GrVariable[] variables = ((GrVariableDeclaration)psi).getVariables();
        if (variables.length > 0) {
          Alignment varName = currentGroup.get(1);
          for (GrVariable variable : variables) {
            myInnerAlignments.put(variable.getNameIdentifierGroovy(), varName);
          }

          if (classLevel && mySettings.ALIGN_GROUP_FIELD_DECLARATIONS) {
            ContainerUtil.putIfNotNull(((GrVariableDeclaration)psi).getTypeElementGroovy(), currentGroup.get(0), myInnerAlignments);

            ASTNode eq = variables[variables.length - 1].getNode().findChildByType(GroovyTokenTypes.mASSIGN);
            if (eq != null) {
              myInnerAlignments.put(eq.getPsi(), currentGroup.get(2));
            }
          }
        }
      }
      else if (GeeseUtil.isClosureRBrace(psi) && myGroovySettings.USE_FLYING_GEESE_BRACES) {
        myInnerAlignments.put(psi, GeeseUtil.calculateRBraceAlignment(psi, myInnerAlignments));
      }
      else {
        if (psi instanceof PsiComment) {
          PsiElement prev = psi.getPrevSibling();
          if (prev != null && prev.getNode().getElementType() != mNLS || classLevel && !fieldGroupEnded(psi)) {
            continue;
          }
        }
        currentGroup = null;
      }
    }
  }

  private boolean fieldGroupEnded(PsiElement psi) {
    if (!mySettings.ALIGN_GROUP_FIELD_DECLARATIONS) return true;
    PsiElement prevSibling = psi.getPrevSibling();
    return prevSibling != null && StringUtil.countChars(prevSibling.getText(), '\n') >= mySettings.KEEP_BLANK_LINES_IN_DECLARATIONS;
  }

  private static List<LeafPsiElement> getSpockTable(GrStatement statement) {
    LinkedList<LeafPsiElement> result = new LinkedList<LeafPsiElement>();
    while (isTablePart(statement)) {
      result.addFirst((LeafPsiElement)((GrBinaryExpression)statement).getOperationToken());
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

  private boolean mustAlign(PsiElement blockPsi, List<ASTNode> children) {
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

    if (blockPsi instanceof GrAssignmentExpression && ((GrAssignmentExpression)blockPsi).getRValue() instanceof GrAssignmentExpression) {
      return mySettings.ALIGN_MULTILINE_ASSIGNMENT;
    }

    return blockPsi instanceof GrParameterList && mySettings.ALIGN_MULTILINE_PARAMETERS ||
        blockPsi instanceof GrExtendsClause && mySettings.ALIGN_MULTILINE_EXTENDS_LIST ||
        blockPsi instanceof GrThrowsClause && mySettings.ALIGN_MULTILINE_THROWS_LIST ||
        blockPsi instanceof GrConditionalExpression && mySettings.ALIGN_MULTILINE_TERNARY_OPERATION;
  }

  private static boolean isListLikeClause(PsiElement blockPsi) {
    return blockPsi instanceof GrParameterList ||
        blockPsi instanceof GrArgumentList ||
        blockPsi instanceof GrAssignmentExpression ||
        blockPsi instanceof GrConditionalExpression ||
        blockPsi instanceof GrExtendsClause ||
        blockPsi instanceof GrThrowsClause;
  }

  private static boolean isKeyword(ASTNode node) {
    if (node == null) return false;
    
    return TokenSets.KEYWORDS.contains(node.getElementType()) ||
        TokenSets.BRACES.contains(node.getElementType()) && !PlatformPatterns.psiElement().withText(")").withParent(GrArgumentList.class).afterLeaf(",").accepts(node.getPsi());
  }


  private List<Block> generateForMultiLineString() {
    final ArrayList<Block> subBlocks = new ArrayList<Block>();
    final int start = myNode.getTextRange().getStartOffset();
    final int end = myNode.getTextRange().getEndOffset();

    subBlocks.add(new GroovyBlock(myNode, myAlignment, Indent.getNoneIndent(), myWrap, mySettings, myGroovySettings, myInnerAlignments) {
      @NotNull
      public TextRange getTextRange() {
        return new TextRange(start, start + 3);
      }
    });
    subBlocks.add(new GroovyBlock(myNode, myAlignment, Indent.getAbsoluteNoneIndent(), myWrap, mySettings, myGroovySettings, myInnerAlignments) {
      @NotNull
      public TextRange getTextRange() {
        return new TextRange(start + 3, end - 3);
      }
    });
    subBlocks.add(new GroovyBlock(myNode, myAlignment, Indent.getAbsoluteNoneIndent(), myWrap, mySettings, myGroovySettings, myInnerAlignments) {
      @NotNull
      public TextRange getTextRange() {
        return new TextRange(end - 3, end);
      }
    });
    return subBlocks;
  }

  private List<Block> generateForMultiLineGStringBegin() {
    final ArrayList<Block> subBlocks = new ArrayList<Block>();
    final int start = myNode.getTextRange().getStartOffset();
    final int end = myNode.getTextRange().getEndOffset();

    subBlocks.add(new GroovyBlock(myNode, myAlignment, Indent.getNoneIndent(), myWrap, mySettings, myGroovySettings, myInnerAlignments) {
      @NotNull
      public TextRange getTextRange() {
        return new TextRange(start, start + 3);
      }
    });
    subBlocks.add(new GroovyBlock(myNode, myAlignment, Indent.getAbsoluteNoneIndent(), myWrap, mySettings, myGroovySettings, myInnerAlignments) {
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
   */
  private List<Block> generateForBinaryExpr() {
    final ArrayList<Block> subBlocks = new ArrayList<Block>();
    Alignment alignment = mySettings.ALIGN_MULTILINE_BINARY_OPERATION ? Alignment.createAlignment() : null;

    GrBinaryExpression binary = (GrBinaryExpression)myNode.getPsi();
    LOG.assertTrue(binary != null);
    addBinaryChildrenRecursively(binary, subBlocks, Indent.getContinuationWithoutFirstIndent(), alignment
    );
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
  private void addBinaryChildrenRecursively(PsiElement elem, List<Block> list, Indent indent, Alignment alignment) {
    if (elem == null) return;
    // For binary expressions
    if ((elem instanceof GrBinaryExpression)) {
      GrBinaryExpression myExpr = ((GrBinaryExpression) elem);
      if (myExpr.getLeftOperand() instanceof GrBinaryExpression) {
        addBinaryChildrenRecursively(myExpr.getLeftOperand(), list, Indent.getContinuationWithoutFirstIndent(), alignment
        );
      }
      PsiElement op = ((GrBinaryExpression)elem).getOperationToken();
      for (ASTNode childNode : visibleChildren(elem.getNode())) {
        PsiElement psi = childNode.getPsi();
        if (!(psi instanceof GrBinaryExpression)) {
          Alignment alignmentToUse = op == psi ? myInnerAlignments.get(op) : alignment;
          list.add(new GroovyBlock(childNode, alignmentToUse, indent, myWrap, mySettings, myGroovySettings, myInnerAlignments));
        }
      }
      if (myExpr.getRightOperand() instanceof GrBinaryExpression) {
        addBinaryChildrenRecursively(myExpr.getRightOperand(), list, Indent.getContinuationWithoutFirstIndent(), alignment
        );
      }
    }
  }


  private void addNestedChildren(final PsiElement elem,
                                        List<Block> list,
                                        @Nullable final Alignment alignment,
                                        final boolean topLevel) {
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
            processNestedChildrenPrefix(list, alignment, false, grandChildren, i);
          }
          if (i < grandChildren.size()) {
            LOG.assertTrue(nameElement == grandChildren.get(i).getPsi());
            list.add(new MethodCallWithoutQualifierBlock(nameElement, null, myWrap, mySettings, myGroovySettings, topLevel, children, elem,
                                                         myInnerAlignments));
          }
          return;
        }
      }

    }


    processNestedChildrenPrefix(list, alignment, topLevel, children, children.size());
  }

  private void processNestedChildrenPrefix(List<Block> list, Alignment alignment, boolean topLevel, List<ASTNode> children, int limit) {
    ASTNode fst = children.get(0);
    LOG.assertTrue(limit > 0);
    if (NESTED.contains(fst.getElementType())) {
      addNestedChildren(fst.getPsi(), list, alignment, false);
    }
    else {
      Alignment alignmentToUse = myInnerAlignments.get(fst.getPsi());
      Indent indent = Indent.getContinuationWithoutFirstIndent();
      list.add(new GroovyBlock(fst, alignmentToUse, indent, myWrap, mySettings, myGroovySettings, myInnerAlignments));
    }
    addNestedChildrenSuffix(list, alignment, topLevel, children, limit);
  }

  void addNestedChildrenSuffix(List<Block> list, Alignment alignment, boolean topLevel, List<ASTNode> children, int limit) {
    for (int i = 1; i < limit; i++) {
      ASTNode childNode = children.get(i);
      if (canBeCorrectBlock(childNode)) {
        IElementType type = childNode.getElementType();
        Indent indent = topLevel || NESTED.contains(type) || type == mIDENT || TokenSets.DOTS.contains(type) ?
                        Indent.getContinuationWithoutFirstIndent() :
                        Indent.getNoneIndent();
        Alignment alignmentToUse = TokenSets.DOTS.contains(type) ? alignment : myInnerAlignments.get(childNode.getPsi());
        list.add(new GroovyBlock(childNode, alignmentToUse, indent, myWrap, mySettings, myGroovySettings, myInnerAlignments));
      }
    }
  }
}
