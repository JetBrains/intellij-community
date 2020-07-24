// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.formatter.blocks;

import com.intellij.formatting.*;
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
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.templateLanguages.OuterLanguageElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyLanguage;
import org.jetbrains.plugins.groovy.formatter.AlignmentProvider;
import org.jetbrains.plugins.groovy.formatter.FormattingContext;
import org.jetbrains.plugins.groovy.formatter.processors.GroovyIndentProcessor;
import org.jetbrains.plugins.groovy.formatter.processors.GroovyWrappingProcessor;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase;
import org.jetbrains.plugins.groovy.lang.psi.GroovyTokenSets;
import org.jetbrains.plugins.groovy.lang.psi.api.GrArrayInitializer;
import org.jetbrains.plugins.groovy.lang.psi.api.GrTryResourceList;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrThrowsClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentLabel;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrCodeBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.clauses.GrCaseSection;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.clauses.GrTraditionalForClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrString;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameterList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrExtendsClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinitionBody;
import org.jetbrains.plugins.groovy.lang.psi.util.GrStringUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static com.intellij.formatting.Indent.*;
import static org.jetbrains.plugins.groovy.formatter.blocks.BlocksKt.flattenQualifiedReference;
import static org.jetbrains.plugins.groovy.formatter.blocks.BlocksKt.shouldHandleAsSimpleClosure;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.mLCURLY;
import static org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.T_LPAREN;
import static org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.T_RPAREN;

/**
 * Utility class to generate myBlock hierarchy
 *
 * @author ilyas
 */
public class GroovyBlockGenerator {

  private static final TokenSet NESTED = TokenSet.create(
    GroovyElementTypes.REFERENCE_EXPRESSION,
    GroovyElementTypes.PATH_INDEX_PROPERTY,
    GroovyElementTypes.PATH_METHOD_CALL,
    GroovyElementTypes.PATH_PROPERTY_REFERENCE
  );

  private static final Logger LOG = Logger.getInstance(GroovyBlockGenerator.class);

  private final GroovyBlock myBlock;
  private final ASTNode myNode;

  private final AlignmentProvider myAlignmentProvider;
  private final GroovyWrappingProcessor myWrappingProcessor;

  private FormattingContext myContext;

  public GroovyBlockGenerator(GroovyBlock block) {
    myBlock = block;
    myNode = myBlock.getNode();

    myContext = block.getContext();
    myAlignmentProvider = myContext.getAlignmentProvider();

    myWrappingProcessor = new GroovyWrappingProcessor(myBlock);
  }

  static List<ASTNode> getClosureBodyVisibleChildren(final ASTNode node) {
    List<ASTNode> children = visibleChildren(node);

    if (!children.isEmpty()) {
      ASTNode first = children.get(0);
      if (first.getElementType() == mLCURLY) children.remove(0);
    }

    if (!children.isEmpty()) {
      ASTNode last = children.get(children.size() - 1);
      if (last.getElementType() == GroovyTokenTypes.mRCURLY) children.remove(children.size() - 1);
    }
    return children;
  }


  public List<Block> generateSubBlocks() {

    //For binary expressions
    PsiElement blockPsi = myNode.getPsi();
    IElementType elementType = myNode.getElementType();

    if (blockPsi instanceof GrBinaryExpression && !(blockPsi.getParent() instanceof GrBinaryExpression)) {
      return generateForBinaryExpr();
    }

    //For multiline strings
    if (GroovyTokenSets.STRING_LITERALS.contains(elementType) && myBlock.getTextRange().equals(myNode.getTextRange())) {
      String text = myNode.getText();
      if (text.length() > 6) {
        if (text.startsWith("'''") && text.endsWith("'''") ||
            text.startsWith("\"\"\"") && text.endsWith("\"\"\"")) {
          return generateForMultiLineString();
        }
      }
    }

    //for gstrings
    if (elementType == GroovyElementTypes.GSTRING ||
        elementType == GroovyElementTypes.REGEX ||
        elementType == GroovyTokenTypes.mREGEX_LITERAL ||
        elementType == GroovyTokenTypes.mDOLLAR_SLASH_REGEX_LITERAL) {
      boolean isPlainGString = myNode.getPsi() instanceof GrString && ((GrString)myNode.getPsi()).isPlainString();
      final FormattingContext context = isPlainGString ? myContext.createContext(true, true) : myContext.createContext(false, true);

      final ArrayList<Block> subBlocks = new ArrayList<>();
      ASTNode[] children = getGroovyChildren(myNode);
      for (ASTNode childNode : children) {
        if (childNode.getTextRange().getLength() > 0) {
          subBlocks.add(new GroovyBlock(childNode, getIndent(childNode), Wrap.createWrap(WrapType.NONE, false), context));
        }
      }
      return subBlocks;
    }

    final CommonCodeStyleSettings settings = myContext.getSettings();
    // chained properties, calls, indexing, etc
    if (NESTED.contains(elementType) && blockPsi.getParent() != null && !NESTED.contains(blockPsi.getParent().getNode().getElementType())) {
      final List<Block> subBlocks = new ArrayList<>();
      AlignmentProvider.Aligner dotsAligner = settings.ALIGN_MULTILINE_CHAINED_METHODS ? myAlignmentProvider.createAligner(false) : null;

      final Wrap wrap = myWrappingProcessor.getChainedMethodCallWrap();
      addNestedChildren(myNode.getPsi(), subBlocks, dotsAligner, true, wrap);
      return subBlocks;
    }

    if (blockPsi instanceof GrListOrMap && ((GrListOrMap)blockPsi).isMap() && myContext.getGroovySettings().ALIGN_NAMED_ARGS_IN_MAP) {
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

    if (blockPsi instanceof GrParameterList) {
      final List<ASTNode> children = visibleChildren(myNode);
      if (settings.ALIGN_MULTILINE_METHOD_BRACKETS) {
        PsiElement lParen = ((GrParameterList)blockPsi).getLParen();
        if (lParen != null) {
          PsiElement rParen = ((GrParameterList)blockPsi).getRParen();
          if (rParen != null) {
            myAlignmentProvider.addPair(lParen, rParen, false);
          }
        }
      }
      if (settings.ALIGN_MULTILINE_PARAMETERS) {
        final AlignmentProvider.Aligner aligner = myAlignmentProvider.createAligner(false);
        for (ASTNode node : children) {
          if (isKeyword(node)) continue;
          IElementType type = node.getElementType();
          if (type == T_LPAREN || type == T_RPAREN) continue;
          aligner.append(node.getPsi());
        }
      }
      return getGenericBlocks(children);
    }

    // For Parameter lists
    if (isListLikeClause(blockPsi)) {
      List<ASTNode> astNodes = visibleChildren(myNode);

      if (mustAlign(blockPsi, astNodes)) {
        final AlignmentProvider.Aligner aligner = myAlignmentProvider.createAligner(false);
        for (ASTNode node : astNodes) {
          if (!isKeyword(node)) aligner.append(node.getPsi());
        }
      }
      return getGenericBlocks(astNodes);
    }

    if (blockPsi instanceof GrSwitchStatement) {
      final ArrayList<Block> subBlocks = new ArrayList<>();
      final ArrayList<Block> bodyBlocks = new ArrayList<>();
      List<ASTNode> astNodes = visibleChildren(myNode);
      boolean switchBody = false;
      for (ASTNode childNode : astNodes) {
        if (childNode.getElementType() == mLCURLY) {
          switchBody = true;
        }

        if (switchBody) {
          bodyBlocks.add(new GroovyBlock(childNode, getIndent(childNode), getChildWrap(childNode), myContext));
        }
        else {
          subBlocks.add(new GroovyBlock(childNode, getIndent(childNode), getChildWrap(childNode), myContext));
        }
      }
      if (!bodyBlocks.isEmpty()) {
        subBlocks.add(createSwitchBodyBlock(bodyBlocks));
      }
      return subBlocks;
    }

    boolean classLevel = blockPsi instanceof GrTypeDefinitionBody;
    if (blockPsi instanceof GrClosableBlock &&
        ((GrClosableBlock)blockPsi).getArrow() != null &&
        ((GrClosableBlock)blockPsi).getParameters().length > 0 &&
        !getClosureBodyVisibleChildren(myNode).isEmpty()) {
      GrClosableBlock closableBlock = (GrClosableBlock)blockPsi;

      ArrayList<Block> blocks = new ArrayList<>();

      PsiElement lbrace = closableBlock.getLBrace();
      if (lbrace != null) {
        ASTNode node = lbrace.getNode();
        blocks.add(new GroovyBlock(node, getIndent(node), Wrap.createWrap(WrapType.NONE, false), myContext));
      }

      {
        Indent indent = getNormalIndent();
        ASTNode parameterListNode = closableBlock.getParameterList().getNode();
        boolean simpleClosure = shouldHandleAsSimpleClosure(closableBlock, settings);
        FormattingContext closureContext = myContext.createContext(simpleClosure, simpleClosure);
        ClosureBodyBlock bodyBlock = new ClosureBodyBlock(parameterListNode, indent, Wrap.createWrap(WrapType.NONE, false), closureContext);
        blocks.add(bodyBlock);
      }

      PsiElement rbrace = closableBlock.getRBrace();
      if (rbrace != null) {
        ASTNode node = rbrace.getNode();
        blocks.add(new GroovyBlock(node, getIndent(node), Wrap.createWrap(WrapType.NONE, false), myContext));
      }

      return blocks;
    }

    if (blockPsi instanceof GrClosableBlock) {
      FormattingContext oldContext = myContext;
      try {
        boolean simpleClosure = shouldHandleAsSimpleClosure((GrClosableBlock)blockPsi, settings);
        myContext = myContext.createContext(simpleClosure, simpleClosure);
        return generateCodeSubBlocks(visibleChildren(myNode));
      } finally {
        myContext = oldContext;
      }
    }

    if (blockPsi instanceof GrCodeBlock || blockPsi instanceof GroovyFile) {
      return generateCodeSubBlocks(visibleChildren(myNode));
    }
    if (classLevel) {
      List<ASTNode> children = visibleChildren(myNode);
      calculateAlignments(children, true);
      return generateSubBlocks(children);
    }

    if (blockPsi instanceof GrTraditionalForClause) {
      if (settings.ALIGN_MULTILINE_FOR) {
        final GrTraditionalForClause clause = (GrTraditionalForClause)blockPsi;
        final AlignmentProvider.Aligner parenthesesAligner = myAlignmentProvider.createAligner(false);
        parenthesesAligner.append(clause.getInitialization());
        parenthesesAligner.append(clause.getCondition());
        parenthesesAligner.append(clause.getUpdate());
      }
    }

    else if (blockPsi instanceof GrBinaryExpression) {
      if (settings.ALIGN_MULTILINE_BINARY_OPERATION) {
        final GrBinaryExpression binary = (GrBinaryExpression)blockPsi;

        final GrExpression left = binary.getLeftOperand();
        final GrExpression right = binary.getRightOperand();
        if (right != null) {
          myAlignmentProvider.addPair(left, right, false);
        }
      }
    }

    else if (blockPsi instanceof GrAssignmentExpression) {
      if (settings.ALIGN_MULTILINE_ASSIGNMENT) {
        final GrAssignmentExpression assignment = (GrAssignmentExpression)blockPsi;

        final GrExpression lValue = assignment.getLValue();
        final GrExpression rValue = assignment.getRValue();
        if (rValue != null) {
          myAlignmentProvider.addPair(lValue, rValue, false);
        }
      }
    }

    else if (blockPsi instanceof GrConditionalExpression) {
      if (settings.ALIGN_MULTILINE_TERNARY_OPERATION) {
        final GrConditionalExpression conditional = (GrConditionalExpression)blockPsi;

        final AlignmentProvider.Aligner exprAligner = myAlignmentProvider.createAligner(false);
        exprAligner.append(conditional.getCondition());
        if (!(conditional instanceof GrElvisExpression)) {
          exprAligner.append(conditional.getThenBranch());
        }
        exprAligner.append(conditional.getElseBranch());

        ASTNode question = conditional.getNode().findChildByType(GroovyTokenTypes.mQUESTION);
        ASTNode colon = conditional.getNode().findChildByType(GroovyTokenTypes.mCOLON);
        if (question != null && colon != null) {
          AlignmentProvider.Aligner questionColonAligner = myAlignmentProvider.createAligner(false);
          questionColonAligner.append(question.getPsi());
          questionColonAligner.append(colon.getPsi());
        }
      }
    }

    // For other cases
    return getGenericBlocks(visibleChildren(myNode));
  }

  @NotNull
  private List<Block> getGenericBlocks(@NotNull List<ASTNode> astNodes) {
    return ContainerUtil.map(astNodes, it -> new GroovyBlock(it, getIndent(it), getChildWrap(it), myContext));
  }

  private Block createSwitchBodyBlock(List<Block> bodyBlocks) {
    CommonCodeStyleSettings settings = myContext.getSettings();
    return new SyntheticGroovyBlock(
      bodyBlocks,
      Wrap.createWrap(WrapType.NONE, false),
      GroovyIndentProcessor.getBlockIndent(settings.BRACE_STYLE),
      GroovyIndentProcessor.getIndentInBlock(settings.BRACE_STYLE),
      myContext) {

      @NotNull
      @Override
      public ChildAttributes getChildAttributes(int newChildIndex) {
        List<Block> subBlocks = getSubBlocks();
        if (newChildIndex > 0) {
          Block block = subBlocks.get(newChildIndex - 1);
          if (block instanceof GroovyBlock) {
            PsiElement anchorPsi = ((GroovyBlock)block).getNode().getPsi();
            if (anchorPsi instanceof GrCaseSection) {
              boolean finished = GroovyIndentProcessor.isFinishedCase((GrCaseSection)anchorPsi, Integer.MAX_VALUE);
              Indent indent = GroovyIndentProcessor.getSwitchCaseIndent(settings);
              int indentSize = 0;
              CommonCodeStyleSettings.IndentOptions options = settings.getIndentOptions();
              if (options != null) {
                indentSize = options.INDENT_SIZE;
              }
              if (!finished) {
                indent = getSpaceIndent((indent.getType() == Type.NORMAL ? indentSize : 0) + indentSize);
              }
              return new ChildAttributes(indent, null);
            }
          }
        }
        return super.getChildAttributes(newChildIndex);
      }
    };
  }

  private Wrap getChildWrap(ASTNode childNode) {
    return myWrappingProcessor.getChildWrap(childNode);
  }

  @NotNull
  List<Block> generateCodeSubBlocks(final List<ASTNode> children) {
    final ArrayList<Block> subBlocks = new ArrayList<>();

    List<ASTNode> flattenChildren = flattenChildren(children);
    calculateAlignments(flattenChildren, false);
    for (int i = 0; i < flattenChildren.size(); i++) {
      ASTNode childNode = flattenChildren.get(i);
      if (childNode.getElementType() == GroovyElementTypes.LABELED_STATEMENT) {
        int start = i;
        do {
          i++;
        }
        while (i < flattenChildren.size() &&
               flattenChildren.get(i).getElementType() != GroovyElementTypes.LABELED_STATEMENT &&
               flattenChildren.get(i).getElementType() != GroovyTokenTypes.mRCURLY);
        subBlocks.add(
          new GrLabelBlock(
            childNode,
            flattenChildren.subList(start + 1, i),
            getIndent(childNode),
            getChildWrap(childNode),
            myContext)
        );
        i--;
      }
      else {
        subBlocks.add(new GroovyBlock(childNode, getIndent(childNode), getChildWrap(childNode), myContext));
      }
    }

    return subBlocks;
  }

  List<Block> generateSubBlocks(List<ASTNode> children) {
    final List<Block> subBlocks = new ArrayList<>();
    for (ASTNode childNode : children) {
      subBlocks.add(new GroovyBlock(childNode, getIndent(childNode), getChildWrap(childNode), myContext));
    }
    return subBlocks;
  }

  private static List<ASTNode> flattenChildren(List<ASTNode> children) {
    ArrayList<ASTNode> result = new ArrayList<>();
    for (ASTNode child : children) {
      processNodeFlattening(result, child);
    }
    return result;
  }

  private static void processNodeFlattening(ArrayList<ASTNode> result, ASTNode child) {
    result.add(child);
    if (child.getElementType() == GroovyElementTypes.LABELED_STATEMENT) {
      for (ASTNode node : visibleChildren(child)) {
        processNodeFlattening(result, node);
      }
    }
  }

  private Indent getIndent(ASTNode childNode) {
    return new GroovyIndentProcessor().getChildIndent(myBlock, childNode);
  }


  private void calculateAlignments(List<ASTNode> children, boolean classLevel) {
    List<GrStatement> currentGroup = null;
    boolean spock = true;
    for (ASTNode child : children) {
      PsiElement psi = child.getPsi();
      if (psi instanceof GrLabeledStatement) {
        alignGroup(currentGroup, spock, classLevel);
        currentGroup = new ArrayList<>();
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
            currentGroup = new ArrayList<>();
            spock = false;
          }
          currentGroup.add((GrStatement)psi);
        }
      }
      else {
        if (shouldSkip(classLevel, psi)) continue;
        alignGroup(currentGroup, spock, classLevel);
        currentGroup = null;
      }
    }

    if (currentGroup != null) {
      alignGroup(currentGroup, spock, classLevel);
    }
  }

  private boolean shouldSkip(boolean classLevel, PsiElement psi) {
    if (psi instanceof PsiComment) {
      PsiElement prev = psi.getPrevSibling();
      if (prev != null) {
        if (!classLevel || !PsiUtil.isNewLine(prev) || !fieldGroupEnded(psi)) {
          return true;
        }
      }
    }
    if (psi.getParent() instanceof GrLabeledStatement) {
      if (psi instanceof GrLiteral && GrStringUtil.isStringLiteral((GrLiteral)psi) //skip string comments at the beginning of spock table
          || !(psi instanceof GrStatement)) {
        return true;
      }
    }
    return false;
  }

  private void alignGroup(@Nullable List<GrStatement> group, boolean spock, boolean classLevel) {
    if (group == null) {
      return;
    }
    if (spock) {
      alignSpockTable(group);
    }
    else {
      alignVariableDeclarations(group, classLevel);
    }
  }

  private void alignVariableDeclarations(List<GrStatement> group, boolean classLevel) {
    AlignmentProvider.Aligner typeElement = myAlignmentProvider.createAligner(true);
    AlignmentProvider.Aligner varName = myAlignmentProvider.createAligner(true);
    AlignmentProvider.Aligner eq = myAlignmentProvider.createAligner(true);
    for (GrStatement statement : group) {
      GrVariableDeclaration varDeclaration = (GrVariableDeclaration)statement;
      GrVariable[] variables = varDeclaration.getVariables();
      for (GrVariable variable : variables) {
        varName.append(variable.getNameIdentifierGroovy());
      }

      if (classLevel && myContext.getSettings().ALIGN_GROUP_FIELD_DECLARATIONS) {
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
    GrStatement inner = group.get(0);
    boolean embedded = inner != null && isTablePart(inner);

    GrStatement first = embedded ? inner : group.get(1);
    List<AlignmentProvider.Aligner> alignments = ContainerUtil
      .map2List(getSpockTable(first), leaf -> myAlignmentProvider.createAligner(leaf, true, Alignment.Anchor.RIGHT));

    int second = embedded ? 1 : 2;
    for (int i = second; i < group.size(); i++) {
      List<LeafPsiElement> table = getSpockTable(group.get(i));
      for (int j = 0; j < Math.min(table.size(), alignments.size()); j++) {
        alignments.get(j).append(table.get(j));
      }
    }
  }

  private boolean fieldGroupEnded(PsiElement psi) {
    if (!myContext.getSettings().ALIGN_GROUP_FIELD_DECLARATIONS) return true;
    PsiElement prevSibling = psi.getPrevSibling();
    return prevSibling != null &&
           StringUtil.countChars(prevSibling.getText(), '\n') >= myContext.getSettings().KEEP_BLANK_LINES_IN_DECLARATIONS;
  }

  private static List<LeafPsiElement> getSpockTable(GrStatement statement) {
    List<LeafPsiElement> result = new ArrayList<>();
    statement.accept(new GroovyElementVisitor() {
      @Override
      public void visitBinaryExpression(@NotNull GrBinaryExpression expression) {
        if (isTablePart(expression)) {
          result.add((LeafPsiElement)expression.getOperationToken());
          expression.acceptChildren(this);
        }
      }
    });
    result.sort(Comparator.comparingInt(TreeElement::getStartOffset));
    return result;
  }

  private static boolean isTablePart(PsiElement psi) {
    return psi instanceof GrBinaryExpression &&
           (GroovyTokenTypes.mBOR == ((GrBinaryExpression)psi).getOperationTokenType() ||
            GroovyTokenTypes.mLOR == ((GrBinaryExpression)psi).getOperationTokenType());
  }

  public static List<ASTNode> visibleChildren(ASTNode node) {
    ArrayList<ASTNode> list = new ArrayList<>();
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
    if (blockPsi instanceof GrArgumentList && myContext.getSettings().ALIGN_MULTILINE_PARAMETERS_IN_CALLS) {
      return !(children.size() == 3 &&
               children.get(0).getElementType() == GroovyTokenTypes.mLPAREN &&
               (children.get(1).getElementType() == GroovyElementTypes.CLOSABLE_BLOCK || children.get(1).getElementType() ==
                                                                                         GroovyElementTypes.LIST_OR_MAP) &&
               children.get(2).getElementType() == GroovyTokenTypes.mRPAREN);
    }

    if (blockPsi instanceof GrAssignmentExpression && ((GrAssignmentExpression)blockPsi).getRValue() instanceof GrAssignmentExpression) {
      return myContext.getSettings().ALIGN_MULTILINE_ASSIGNMENT;
    }

    return blockPsi instanceof GrExtendsClause && myContext.getSettings().ALIGN_MULTILINE_EXTENDS_LIST ||
           blockPsi instanceof GrThrowsClause && myContext.getSettings().ALIGN_MULTILINE_THROWS_LIST ||
           blockPsi instanceof GrListOrMap && myContext.getGroovySettings().ALIGN_MULTILINE_LIST_OR_MAP ||
           blockPsi instanceof GrTryResourceList && myContext.getSettings().ALIGN_MULTILINE_RESOURCES ||
           blockPsi instanceof GrArrayInitializer && myContext.getSettings().ALIGN_MULTILINE_ARRAY_INITIALIZER_EXPRESSION;
  }

  private static boolean isListLikeClause(PsiElement blockPsi) {
    return blockPsi instanceof GrArgumentList ||
           blockPsi instanceof GrAssignmentExpression ||
           blockPsi instanceof GrExtendsClause ||
           blockPsi instanceof GrThrowsClause ||
           blockPsi instanceof GrListOrMap ||
           blockPsi instanceof GrTryResourceList ||
           blockPsi instanceof GrArrayInitializer;
  }

  private static boolean isKeyword(ASTNode node) {
    if (node == null) return false;

    return TokenSets.KEYWORDS.contains(node.getElementType()) ||
           TokenSets.BRACES.contains(node.getElementType()) &&
           !PlatformPatterns.psiElement().withText(")").withParent(GrArgumentList.class).afterLeaf(",").accepts(node.getPsi());
  }


  private List<Block> generateForMultiLineString() {
    final ArrayList<Block> subBlocks = new ArrayList<>();
    final int start = myNode.getTextRange().getStartOffset();
    final int end = myNode.getTextRange().getEndOffset();

    subBlocks.add(
      new GroovyBlockWithRange(myNode, getNoneIndent(), new TextRange(start, start + 3), Wrap.createWrap(WrapType.NONE, false), myContext));
    subBlocks.add(
      new GroovyBlockWithRange(myNode, getAbsoluteNoneIndent(), new TextRange(start + 3, end - 3), Wrap.createWrap(WrapType.NONE, false),
                               myContext));
    subBlocks.add(
      new GroovyBlockWithRange(myNode, getAbsoluteNoneIndent(), new TextRange(end - 3, end), Wrap.createWrap(WrapType.NONE, false),
                               myContext));
    return subBlocks;
  }

  /**
   * @param node Tree node
   * @return true, if the current node can be myBlock node, else otherwise
   */
  private static boolean canBeCorrectBlock(final ASTNode node) {
    return !node.getText().trim().isEmpty();
  }


  private static ASTNode[] getGroovyChildren(final ASTNode node) {
    PsiElement psi = node.getPsi();
    if (psi instanceof OuterLanguageElement) {
      TextRange range = node.getTextRange();
      ArrayList<ASTNode> childList = new ArrayList<>();
      PsiFile groovyFile = psi.getContainingFile().getViewProvider().getPsi(GroovyLanguage.INSTANCE);
      if (groovyFile instanceof GroovyFileBase) {
        addChildNodes(groovyFile, childList, range, psi);
      }
      return childList.toArray(ASTNode.EMPTY_ARRAY);
    }
    return node.getChildren(null);
  }

  private static void addChildNodes(PsiElement elem, ArrayList<ASTNode> childNodes, TextRange range, PsiElement root) {
    ASTNode node = elem.getNode();
    if (range.contains(elem.getTextRange()) && node != null && elem != root && !(elem instanceof PsiFile)) {
      childNodes.add(node);
    } else {
      for (PsiElement child : elem.getChildren()) {
        addChildNodes(child, childNodes, range, root);
      }
    }
  }

  /**
   * Generates blocks for binary expressions
   *
   * @return
   */
  private List<Block> generateForBinaryExpr() {
    final ArrayList<Block> subBlocks = new ArrayList<>();
    AlignmentProvider.Aligner
      alignment = myContext.getSettings().ALIGN_MULTILINE_BINARY_OPERATION ? myAlignmentProvider.createAligner(false) : null;

    GrBinaryExpression binary = (GrBinaryExpression)myNode.getPsi();
    LOG.assertTrue(binary != null);
    addBinaryChildrenRecursively(binary, subBlocks, getContinuationWithoutFirstIndent(), alignment);
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
        addBinaryChildrenRecursively(myExpr.getLeftOperand(), list, getContinuationWithoutFirstIndent(), aligner);
      }
      PsiElement op = ((GrBinaryExpression)elem).getOperationToken();
      for (ASTNode childNode : visibleChildren(elem.getNode())) {
        PsiElement psi = childNode.getPsi();
        if (!(psi instanceof GrBinaryExpression)) {
          if (op != psi && aligner != null) {
            aligner.append(psi);
          }
          list.add(new GroovyBlock(childNode, indent, getChildWrap(childNode), myContext));
        }
      }
      if (myExpr.getRightOperand() instanceof GrBinaryExpression) {
        addBinaryChildrenRecursively(myExpr.getRightOperand(), list, getContinuationWithoutFirstIndent(), aligner
        );
      }
    }
  }


  private void addNestedChildren(@NotNull PsiElement elem,
                                 @NotNull List<Block> list,
                                 @Nullable AlignmentProvider.Aligner aligner,
                                 final boolean topLevel,
                                 @NotNull Wrap wrap) {
    final List<ASTNode> children = visibleChildren(elem.getNode());
    List<ASTNode> nodes = flattenQualifiedReference(elem);
    if (nodes != null && !nodes.isEmpty()) {
      int i = 0;
      while (i < nodes.size()) {
        ASTNode node = nodes.get(i);
        if (TokenSets.DOTS.contains(node.getElementType())) break;
        i++;
      }
      if (i == nodes.size()) {
        list.add(new MethodCallWithoutQualifierBlock(wrap, topLevel, nodes, myContext));
        return;
      }

      if (i > 0) {
        processNestedChildrenPrefix(list, aligner, false, nodes.subList(0, i), wrap);
      }
      Wrap synWrap = Wrap.createWrap(WrapType.NONE, false);
      Indent indent = getContinuationWithoutFirstIndent();

      List<Block> childBlocks = new ArrayList<>();
      ASTNode dotNode = nodes.get(i);
      if (aligner != null) {
        aligner.append(dotNode.getPsi());
      }
      childBlocks.add(new GroovyBlock(dotNode, getIndent(dotNode), getChildWrap(dotNode), myContext));

      List<ASTNode> callNodes = nodes.subList(i + 1, nodes.size());
      if (!callNodes.isEmpty()) {
        childBlocks.add(new MethodCallWithoutQualifierBlock(wrap, topLevel, callNodes, myContext));
      }

      SyntheticGroovyBlock synBlock = new SyntheticGroovyBlock(childBlocks, synWrap, indent, indent, myContext);
      list.add(synBlock);

      return;
    }

    processNestedChildrenPrefix(list, aligner, topLevel, children, wrap);
  }

  private static boolean isAfterMultiLineClosure(ASTNode dot) {
    PsiElement dotPsi = dot.getPsi();
    PsiElement prev = PsiUtil.skipWhitespaces(dotPsi.getPrevSibling(), false);
    if (prev != null) {
      if (prev instanceof GrMethodCall) {
        final PsiElement last = prev.getLastChild();
        if (last instanceof GrClosableBlock) {
          return last.getText().contains("\n");
        }
      }
    }

    return false;
  }

  private void processNestedChildrenPrefix(List<Block> list,
                                           @Nullable AlignmentProvider.Aligner aligner,
                                           boolean topLevel,
                                           List<ASTNode> children,
                                           Wrap wrap) {
    LOG.assertTrue(children.size() > 0);
    ASTNode fst = children.get(0);
    if (NESTED.contains(fst.getElementType())) {
      addNestedChildren(fst.getPsi(), list, aligner, false, wrap);
    }
    else {
      Indent indent = getContinuationWithoutFirstIndent();
      list.add(new GroovyBlock(fst, indent, getChildWrap(fst), myContext));
    }
    addNestedChildrenSuffix(list, topLevel, children.subList(1, children.size()));
  }

  void addNestedChildrenSuffix(List<Block> list,
                               boolean topLevel,
                               List<ASTNode> children) {
    for (ASTNode childNode : children) {
      if (canBeCorrectBlock(childNode)) {
        IElementType type = childNode.getElementType();
        Indent indent = NESTED.contains(type)
                        || type == GroovyTokenTypes.mIDENT
                        || TokenSets.DOTS.contains(type) && !isAfterMultiLineClosure(childNode)
                        || topLevel
                        ? getContinuationWithoutFirstIndent() : getNoneIndent();

        list.add(new GroovyBlock(childNode, indent, getChildWrap(childNode), myContext));
      }
    }
  }


}
