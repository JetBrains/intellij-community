// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.plugins.groovy.formatter.blocks;

import com.intellij.formatting.*;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.tree.ILazyParseableElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.formatter.FormattingContext;
import org.jetbrains.plugins.groovy.formatter.processors.GroovyIndentProcessor;
import org.jetbrains.plugins.groovy.formatter.processors.GroovySpacingProcessor;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocComment;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocTag;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotationArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.clauses.GrCaseSection;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrBinaryExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrConditionalExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameterList;

import java.util.List;

import static org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.BLOCK_LAMBDA_BODY;
import static org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.LAMBDA_EXPRESSION;

/**
 * Block implementation for Groovy formatter
 */
public class GroovyBlock implements Block, ASTBlock {

  protected final ASTNode myNode;
  protected Alignment myAlignment = null;
  protected final Indent myIndent;
  protected final Wrap myWrap;

  protected final FormattingContext myContext;

  protected List<Block> mySubBlocks = null;

  /**
   * Consider using {@link FormattingContext#createBlock(ASTNode, Indent, Wrap)}
   */
  public GroovyBlock(final @NotNull ASTNode node,
                     final @NotNull Indent indent,
                     final @Nullable Wrap wrap,
                     @NotNull FormattingContext context) {
    myNode = node;

    myIndent = indent;
    myWrap = wrap;
    myContext = context;
  }

  @Override
  public @NotNull ASTNode getNode() {
    return myNode;
  }

  @Override
  public @NotNull TextRange getTextRange() {
    return myNode.getTextRange();
  }

  @Override
  public @NotNull List<Block> getSubBlocks() {
    if (mySubBlocks == null) {
      mySubBlocks = new GroovyBlockGenerator(this).generateSubBlocks();
    }
    return mySubBlocks;
  }

  @Override
  public @Nullable Wrap getWrap() {
    return myWrap;
  }

  @Override
  public @NotNull Indent getIndent() {
    return myIndent;
  }

  @Override
  public @Nullable Alignment getAlignment() {
    if (myAlignment == null) {
      myAlignment = myContext.getAlignmentProvider().getAlignment(myNode.getPsi());
    }

    return myAlignment;
  }

  /**
   * Returns spacing between neighbour elements
   *
   * @param child1 left element
   * @param child2 right element
   */
  @Override
  public @Nullable Spacing getSpacing(Block child1, @NotNull Block child2) {
    return GroovySpacingProcessor.getSpacing(child1, child2, myContext);
  }

  @Override
  public @NotNull ChildAttributes getChildAttributes(final int newChildIndex) {
    ASTNode astNode = getNode();
    final PsiElement psiParent = astNode.getPsi();
    if (psiParent instanceof GroovyFileBase) {
      return new ChildAttributes(Indent.getNoneIndent(), null);
    }
    if (psiParent instanceof GrSwitchElement) {
      return new ChildAttributes(Indent.getNoneIndent(), null);
    }
    if (psiParent instanceof GrCaseSection) {
      return new ChildAttributes(GroovyIndentProcessor.getSwitchCaseIndent(getContext().getSettings()), null);
    }

    if (astNode.getElementType() == LAMBDA_EXPRESSION) {
      return new ChildAttributes(Indent.getNormalIndent(), null);
    }
    if (astNode.getElementType() == BLOCK_LAMBDA_BODY) {
      return new ChildAttributes(Indent.getNormalIndent(), null);
    }
    if (TokenSets.BLOCK_SET.contains(astNode.getElementType()) || GroovyElementTypes.SWITCH_STATEMENT.equals(astNode.getElementType()) || GroovyElementTypes.SWITCH_EXPRESSION.equals(astNode.getElementType())) {
      return new ChildAttributes(Indent.getNormalIndent(), null);
    }
    if (GroovyElementTypes.CASE_SECTION.equals(astNode.getElementType())) {
      return new ChildAttributes(Indent.getNormalIndent(), null);
    }
    if (psiParent instanceof GrBinaryExpression ||
        psiParent instanceof GrConditionalExpression ||
        psiParent instanceof GrArgumentList ||
        psiParent instanceof GrParameterList ||
        psiParent instanceof GrListOrMap ||
        psiParent instanceof GrAnnotationArgumentList ||
        psiParent instanceof GrVariable ||
        psiParent instanceof GrAssignmentExpression) {
      return new ChildAttributes(Indent.getContinuationWithoutFirstIndent(), null);
    }
    if (psiParent instanceof GrDocComment || psiParent instanceof GrDocTag) {
      return new ChildAttributes(Indent.getSpaceIndent(GroovyIndentProcessor.GDOC_COMMENT_INDENT), null);
    }
    if (psiParent instanceof GrIfStatement || psiParent instanceof GrLoopStatement) {
      return new ChildAttributes(Indent.getNormalIndent(), null);
    }
    if (psiParent instanceof GrLabeledStatement && newChildIndex == 2) {
      final Indent indent = getContext().getGroovySettings().INDENT_LABEL_BLOCKS
                            ? Indent.getLabelIndent()
                            : Indent.getNoneIndent();
      return new ChildAttributes(indent, null);
    }
    return new ChildAttributes(Indent.getNoneIndent(), null);
  }

  @Override
  public boolean isIncomplete() {
    return isIncomplete(myNode);
  }

  /**
   * @param node Tree node
   * @return true if node is incomplete
   */
  public static boolean isIncomplete(final @NotNull ASTNode node) {
    if (node.getElementType() instanceof ILazyParseableElementType) return false;
    ASTNode lastChild = node.getLastChildNode();
    while (lastChild != null &&
           !(lastChild.getElementType() instanceof ILazyParseableElementType) &&
           (lastChild.getPsi() instanceof PsiWhiteSpace || lastChild.getPsi() instanceof PsiComment)) {
      lastChild = lastChild.getTreePrev();
    }
    return lastChild != null && (lastChild.getPsi() instanceof PsiErrorElement || isIncomplete(lastChild));
  }

  @Override
  public boolean isLeaf() {
    return myNode.getFirstChildNode() == null;
  }

  @Override
  public String toString() {
    return getTextRange() + ": " + myNode;
  }


  public FormattingContext getContext() {
    return myContext;
  }
}
