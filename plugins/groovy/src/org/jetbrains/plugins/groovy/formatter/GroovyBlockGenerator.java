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
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.codeStyle.GroovyCodeStyleSettings;
import org.jetbrains.plugins.groovy.formatter.processors.GroovyIndentProcessor;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.GrQualifiedReference;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrThrowsClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrLabeledStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentLabel;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrCodeBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrBinaryExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrConditionalExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameterList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrExtendsClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinitionBody;

import java.util.ArrayList;
import java.util.LinkedList;
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

  private static final Logger LOG = Logger.getInstance(GroovyBlockGenerator.class);

  private final GroovyBlock myBlock;
  private final ASTNode myNode;
  private final Alignment myAlignment;
  private final Wrap myWrap;
  private final CommonCodeStyleSettings mySettings;
  private final AlignmentProvider myAlignmentProvider;
  private final GroovyCodeStyleSettings myGroovySettings;

  public GroovyBlockGenerator(GroovyBlock block) {
    myBlock = block;
    myNode = myBlock.getNode();
    myAlignment = myBlock.getAlignment();
    myWrap = myBlock.getWrap();
    mySettings = myBlock.getSettings();
    myAlignmentProvider = myBlock.getAlignmentProvider();
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
          subBlocks.add(new GroovyBlock(childNode, indent, myWrap, mySettings, myGroovySettings, myAlignmentProvider));
        }
      }
      return subBlocks;
    }

    // chained properties, calls, indexing, etc
    if (NESTED.contains(myNode.getElementType()) && blockPsi.getParent() != null && !NESTED.contains(blockPsi.getParent().getNode().getElementType())) {
      final List<Block> subBlocks = new ArrayList<Block>();
      AlignmentProvider.Aligner dotsAligner = mySettings.ALIGN_MULTILINE_CHAINED_METHODS ? myAlignmentProvider.createAligner(true) : null;
      addNestedChildren(myNode.getPsi(), subBlocks, dotsAligner, true);
      return subBlocks;
    }

    if (blockPsi instanceof GrListOrMap && ((GrListOrMap)blockPsi).isMap() && myGroovySettings.ALIGN_NAMED_ARGS_IN_MAP) {
      AlignmentProvider.Aligner labels = myAlignmentProvider.createAligner(false);
      AlignmentProvider.Aligner exprs = myAlignmentProvider.createAligner(true);
      GrNamedArgument[] namedArgs = ((GrListOrMap)blockPsi).getNamedArguments();
      for (GrNamedArgument arg : namedArgs) {
        GrArgumentLabel label = arg.getLabel();
        if (label != null) labels.append(label);

        PsiElement colon = arg.getColon();
        if (colon == null) colon = arg.getExpression();
        if (colon != null) exprs.append(colon);
      }
    }

    // For Parameter lists
    if (isListLikeClause(blockPsi)) {
      final ArrayList<Block> subBlocks = new ArrayList<Block>();
      List<ASTNode> astNodes = visibleChildren(myNode);

      if (mustAlign(blockPsi, astNodes)) {
        final AlignmentProvider.Aligner aligner = myAlignmentProvider.createAligner(false);
        for (ASTNode node : astNodes) {
          if (!isKeyword(node)) aligner.append(node.getPsi());
        }
      }
      for (ASTNode childNode : astNodes) {
        final Indent indent = GroovyIndentProcessor.getChildIndent(myBlock, childNode);
        subBlocks.add(new GroovyBlock(childNode, indent, myWrap, mySettings, myGroovySettings, myAlignmentProvider));
      }
      return subBlocks;
    }

    boolean classLevel = blockPsi instanceof GrTypeDefinitionBody;
    if (blockPsi instanceof GrCodeBlock || blockPsi instanceof GroovyFile || classLevel) {
      List<ASTNode> children = visibleChildren(myNode);
      calculateAlignments(children, classLevel);
      final ArrayList<Block> subBlocks = new ArrayList<Block>();

      if (classLevel && myAlignment != null) {
        final AlignmentProvider.Aligner aligner = myAlignmentProvider.createAligner(true);
        for (ASTNode child : children) {
          aligner.append(child.getPsi());
        }
      }
      for (ASTNode childNode : children) {
        final Indent indent = GroovyIndentProcessor.getChildIndent(myBlock, childNode);
        subBlocks.add(new GroovyBlock(childNode, indent, myWrap, mySettings, myGroovySettings, myAlignmentProvider));
      }
      return subBlocks;
    }

    // For other cases
    final ArrayList<Block> subBlocks = new ArrayList<Block>();
    for (ASTNode childNode : visibleChildren(myNode)) {
      final Indent indent = GroovyIndentProcessor.getChildIndent(myBlock, childNode);
      subBlocks.add(new GroovyBlock(childNode, indent, myWrap, mySettings, myGroovySettings, myAlignmentProvider));
    }
    return subBlocks;
  }
  

  private void calculateAlignments(List<ASTNode> children, boolean classLevel) {
    List<GrStatement> currentGroup = null;
    boolean spock = true;
    for (ASTNode child : children) {
      PsiElement psi = child.getPsi();
      if (psi instanceof GrLabeledStatement) {
        alignGroup(currentGroup, spock, classLevel);
        currentGroup = ContainerUtil.newArrayList((GrStatement)psi);
        spock = true;
      }
      else if (currentGroup != null && spock && isTablePart(psi)) {
        currentGroup.add((GrStatement)psi);
      }
      else if (psi instanceof GrVariableDeclaration) {
        GrVariable[] variables = ((GrVariableDeclaration)psi).getVariables();
        if (variables.length > 0) {
          if (!classLevel || currentGroup == null || fieldGroupEnded(psi) || spock) {
            alignGroup(currentGroup, spock, classLevel);
            currentGroup = ContainerUtil.newArrayList();
            spock = false;
          }
          currentGroup.add((GrStatement)psi);
        }
      }
      else {
        if (psi instanceof PsiComment) {
          PsiElement prev = psi.getPrevSibling();
          if (prev != null && prev.getNode().getElementType() != mNLS || classLevel && !fieldGroupEnded(psi)) {
            continue;
          }
        }
        alignGroup(currentGroup, spock, classLevel);
        currentGroup = null;
      }
    }
  }

  private void alignGroup(@Nullable List<GrStatement> group, boolean spock, boolean classLevel) {
    if (group == null) {
      return;
    }
    if (spock) {
      alignSpockTable(group);
    } else {
      alignVariableDeclarations(group, classLevel);
    }
  }

  private void alignVariableDeclarations(List<GrStatement> group, boolean classLevel) {
    AlignmentProvider.Aligner typeElement = myAlignmentProvider.createAligner(true);
    AlignmentProvider.Aligner varName = myAlignmentProvider.createAligner(true);
    AlignmentProvider.Aligner eq = myAlignmentProvider.createAligner(true);
    for (GrStatement statement : group) {
      GrVariableDeclaration varDeclaration = (GrVariableDeclaration) statement;
      GrVariable[] variables = varDeclaration.getVariables();
      for (GrVariable variable : variables) {
        varName.append(variable.getNameIdentifierGroovy());
      }

      if (classLevel && mySettings.ALIGN_GROUP_FIELD_DECLARATIONS) {
        typeElement.append(varDeclaration.getTypeElementGroovy());

        ASTNode current_eq = variables[variables.length - 1].getNode().findChildByType(GroovyTokenTypes.mASSIGN);
        if (current_eq != null) {
          eq.append(current_eq.getPsi());
        }
      }
    }
  }

  private void alignSpockTable(List<GrStatement> group) {
    if (group.size() < 2) {
      return;
    }
    GrStatement inner = ((GrLabeledStatement)group.get(0)).getStatement();
    boolean embedded = inner != null && isTablePart(inner);

    GrStatement first = embedded ? inner : group.get(1);
    List<AlignmentProvider.Aligner> alignments = ContainerUtil
      .map2List(getSpockTable(first), new Function<LeafPsiElement, AlignmentProvider.Aligner>() {
        @Override
        public AlignmentProvider.Aligner fun(LeafPsiElement leaf) {
          return myAlignmentProvider.createAligner(leaf, true, Alignment.Anchor.RIGHT);
        }
      });

    int second = embedded ? 1 : 2;
    for (int i = second; i < group.size(); i++) {
      List<LeafPsiElement> table = getSpockTable(group.get(i));
      for (int j = 0; j < Math.min(table.size(), alignments.size()); j++) {
        alignments.get(j).append(table.get(j));
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
    return psi instanceof GrBinaryExpression && (mBOR == ((GrBinaryExpression)psi).getOperationTokenType() || mLOR == ((GrBinaryExpression)psi).getOperationTokenType());
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
      return !(children.size() == 3 &&
               children.get(0).getElementType() == mLPAREN &&
               (children.get(1).getElementType() == CLOSABLE_BLOCK || children.get(1).getElementType() == LIST_OR_MAP) &&
               children.get(2).getElementType() == mRPAREN);
    }

    if (blockPsi instanceof GrAssignmentExpression && ((GrAssignmentExpression)blockPsi).getRValue() instanceof GrAssignmentExpression) {
      return mySettings.ALIGN_MULTILINE_ASSIGNMENT;
    }

    return blockPsi instanceof GrParameterList && mySettings.ALIGN_MULTILINE_PARAMETERS ||
           blockPsi instanceof GrExtendsClause && mySettings.ALIGN_MULTILINE_EXTENDS_LIST ||
           blockPsi instanceof GrThrowsClause && mySettings.ALIGN_MULTILINE_THROWS_LIST ||
           blockPsi instanceof GrConditionalExpression && mySettings.ALIGN_MULTILINE_TERNARY_OPERATION ||
           blockPsi instanceof GrListOrMap && myGroovySettings.ALIGN_MULTILINE_LIST_OR_MAP;
  }

  private static boolean isListLikeClause(PsiElement blockPsi) {
    return blockPsi instanceof GrParameterList ||
        blockPsi instanceof GrArgumentList ||
        blockPsi instanceof GrAssignmentExpression ||
        blockPsi instanceof GrConditionalExpression ||
        blockPsi instanceof GrExtendsClause ||
        blockPsi instanceof GrThrowsClause ||
        blockPsi instanceof GrListOrMap;
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

    subBlocks.add(new GroovyBlock(myNode, Indent.getNoneIndent(), myWrap, mySettings, myGroovySettings, myAlignmentProvider) {
      @NotNull
      public TextRange getTextRange() {
        return new TextRange(start, start + 3);
      }
    });
    subBlocks.add(new GroovyBlock(myNode, Indent.getAbsoluteNoneIndent(), myWrap, mySettings, myGroovySettings, myAlignmentProvider) {
      @NotNull
      public TextRange getTextRange() {
        return new TextRange(start + 3, end - 3);
      }
    });
    subBlocks.add(new GroovyBlock(myNode, Indent.getAbsoluteNoneIndent(), myWrap, mySettings, myGroovySettings, myAlignmentProvider) {
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

    subBlocks.add(new GroovyBlock(myNode, Indent.getNoneIndent(), myWrap, mySettings, myGroovySettings, myAlignmentProvider) {
      @NotNull
      public TextRange getTextRange() {
        return new TextRange(start, start + 3);
      }
    });
    subBlocks.add(new GroovyBlock(myNode, Indent.getAbsoluteNoneIndent(), myWrap, mySettings, myGroovySettings, myAlignmentProvider) {
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
    AlignmentProvider.Aligner
      alignment = mySettings.ALIGN_MULTILINE_BINARY_OPERATION ? myAlignmentProvider.createAligner(true) : null;

    GrBinaryExpression binary = (GrBinaryExpression)myNode.getPsi();
    LOG.assertTrue(binary != null);
    addBinaryChildrenRecursively(binary, subBlocks, Indent.getContinuationWithoutFirstIndent(), alignment);
    return subBlocks;
  }

  /**
   * Adds all children of specified element to given list
   *
   * @param elem
   * @param list
   * @param indent
   * @param aligner
   */
  private void addBinaryChildrenRecursively(PsiElement elem, List<Block> list, Indent indent, @Nullable AlignmentProvider.Aligner aligner) {
    if (elem == null) return;
    // For binary expressions
    if ((elem instanceof GrBinaryExpression)) {
      GrBinaryExpression myExpr = ((GrBinaryExpression) elem);
      if (myExpr.getLeftOperand() instanceof GrBinaryExpression) {
        addBinaryChildrenRecursively(myExpr.getLeftOperand(), list, Indent.getContinuationWithoutFirstIndent(), aligner);
      }
      PsiElement op = ((GrBinaryExpression)elem).getOperationToken();
      for (ASTNode childNode : visibleChildren(elem.getNode())) {
        PsiElement psi = childNode.getPsi();
        if (!(psi instanceof GrBinaryExpression)) {
          if (op != psi && aligner != null) {
            aligner.append(psi);
          }
          list.add(new GroovyBlock(childNode, indent, myWrap, mySettings, myGroovySettings, myAlignmentProvider));
        }
      }
      if (myExpr.getRightOperand() instanceof GrBinaryExpression) {
        addBinaryChildrenRecursively(myExpr.getRightOperand(), list, Indent.getContinuationWithoutFirstIndent(), aligner
        );
      }
    }
  }


  private void addNestedChildren(final PsiElement elem, List<Block> list, @Nullable AlignmentProvider.Aligner aligner, final boolean topLevel) {
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
            processNestedChildrenPrefix(list, aligner, false, grandChildren, i);
          }
          if (i < grandChildren.size()) {
            LOG.assertTrue(nameElement == grandChildren.get(i).getPsi());
            list.add(new MethodCallWithoutQualifierBlock(nameElement, myWrap, mySettings, myGroovySettings, topLevel, children, elem, myAlignmentProvider));
          }
          return;
        }
      }

    }


    processNestedChildrenPrefix(list, aligner, topLevel, children, children.size());
  }

  private void processNestedChildrenPrefix(List<Block> list, @Nullable AlignmentProvider.Aligner aligner, boolean topLevel, List<ASTNode> children, int limit) {
    ASTNode fst = children.get(0);
    LOG.assertTrue(limit > 0);
    if (NESTED.contains(fst.getElementType())) {
      addNestedChildren(fst.getPsi(), list, aligner, false);
    }
    else {
      Indent indent = Indent.getContinuationWithoutFirstIndent();
      list.add(new GroovyBlock(fst, indent, myWrap, mySettings, myGroovySettings, myAlignmentProvider));
    }
    addNestedChildrenSuffix(list, aligner, topLevel, children, limit);
  }

  void addNestedChildrenSuffix(List<Block> list, @Nullable AlignmentProvider.Aligner aligner, boolean topLevel, List<ASTNode> children, int limit) {
    for (int i = 1; i < limit; i++) {
      ASTNode childNode = children.get(i);
      if (canBeCorrectBlock(childNode)) {
        IElementType type = childNode.getElementType();
        Indent indent = topLevel || NESTED.contains(type) || type == mIDENT || TokenSets.DOTS.contains(type) ?
                        Indent.getContinuationWithoutFirstIndent() :
                        Indent.getNoneIndent();


        if (aligner != null && TokenSets.DOTS.contains(type)) {
          aligner.append(childNode.getPsi());
        }

        list.add(new GroovyBlock(childNode, indent, myWrap, mySettings, myGroovySettings, myAlignmentProvider));
      }
    }
  }
}
