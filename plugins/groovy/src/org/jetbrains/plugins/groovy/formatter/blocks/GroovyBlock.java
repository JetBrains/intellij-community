/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

package org.jetbrains.plugins.groovy.formatter.blocks;

import com.intellij.diagnostic.LogMessageEx;
import com.intellij.formatting.*;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.tree.ILazyParseableElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.formatter.FormattingContext;
import org.jetbrains.plugins.groovy.formatter.processors.GroovyIndentProcessor;
import org.jetbrains.plugins.groovy.formatter.processors.GroovySpacingProcessor;
import org.jetbrains.plugins.groovy.formatter.processors.GroovySpacingProcessorBasic;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocComment;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocTag;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotationArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.GrBreakStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.GrContinueStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.GrReturnStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.GrThrowStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.clauses.GrCaseLabel;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.clauses.GrCaseSection;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrBinaryExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCommandArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrConditionalExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameterList;

import java.util.ArrayList;
import java.util.List;

/**
 * Block implementation for Groovy formatter
 *
 * @author ilyas
 */
public class GroovyBlock implements Block, ASTBlock {
  private static final Logger LOG = Logger.getInstance(GroovyBlock.class);

  protected final ASTNode myNode;
  protected Alignment myAlignment = null;
  protected final Indent myIndent;
  protected final Wrap myWrap;

  protected final FormattingContext myContext;

  protected List<Block> mySubBlocks = null;

  public GroovyBlock(@NotNull final ASTNode node,
                     @NotNull final Indent indent,
                     @Nullable final Wrap wrap,
                     @NotNull FormattingContext context) {
    myNode = node;

    myIndent = indent;
    myWrap = wrap;
    myContext = context;
  }

  @Override
  @NotNull
  public ASTNode getNode() {
    return myNode;
  }

  @Override
  @NotNull
  public TextRange getTextRange() {
    return myNode.getTextRange();
  }

  @NotNull
  @Override
  public List<Block> getSubBlocks() {
    if (mySubBlocks == null) {
      try {
        mySubBlocks = new GroovyBlockGenerator(this).generateSubBlocks();
      }
      catch (AssertionError e) {
        final PsiFile file = myNode.getPsi().getContainingFile();
        LogMessageEx.error(LOG, "Formatting failed for file " + file.getName(), e, file.getText(), myNode.getText());
        mySubBlocks = new ArrayList<>();
      }
      catch (RuntimeException e) {
        final PsiFile file = myNode.getPsi().getContainingFile();
        LogMessageEx.error(LOG, "Formatting failed for file " + file.getName(), e, file.getText(), myNode.getText());
        mySubBlocks = new ArrayList<>();
      }
    }
    return  mySubBlocks;
  }

  @Override
  @Nullable
  public Wrap getWrap() {
    return myWrap;
  }

  @Override
  @Nullable
  public Indent getIndent() {
    return myIndent;
  }

  @Override
  @Nullable
  public Alignment getAlignment() {
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
   * @return
   */
  @Override
  @Nullable
  public Spacing getSpacing(Block child1, @NotNull Block child2) {
    if (child1 instanceof GroovyBlock && child2 instanceof GroovyBlock) {
      if (((GroovyBlock)child1).getNode() == ((GroovyBlock)child2).getNode()) {
        return Spacing.getReadOnlySpacing();
      }

      Spacing spacing = new GroovySpacingProcessor(((GroovyBlock)child1), (GroovyBlock)child2, myContext).getSpacing();
      if (spacing != null) {
        return spacing;
      }
      return GroovySpacingProcessorBasic.getSpacing(((GroovyBlock)child1), ((GroovyBlock)child2), myContext);
    }
    return null;
  }

  @Override
  @NotNull
  public ChildAttributes getChildAttributes(final int newChildIndex) {
    ASTNode astNode = getNode();
    final PsiElement psiParent = astNode.getPsi();
    if (psiParent instanceof GroovyFileBase) {
      return new ChildAttributes(Indent.getNoneIndent(), null);
    }
    if (psiParent instanceof GrSwitchStatement) {
      List<Block> subBlocks = getSubBlocks();
      if (newChildIndex > 0) {
        Block block = subBlocks.get(newChildIndex - 1);
        if (block instanceof GroovyBlock) {
          PsiElement anchorPsi = ((GroovyBlock)block).getNode().getPsi();
          if (anchorPsi instanceof GrCaseSection) {
            for (GrStatement statement : ((GrCaseSection)anchorPsi).getStatements()) {
              if (statement instanceof GrBreakStatement ||
                  statement instanceof GrContinueStatement ||
                  statement instanceof GrReturnStatement ||
                  statement instanceof GrThrowStatement) {
                final Indent indent = GroovyIndentProcessor.getSwitchCaseIndent(myContext.getSettings());
                return new ChildAttributes(indent, null);
              }
            }

            int indentSize = myContext.getSettings().getIndentOptions().INDENT_SIZE;
            final int spaces = myContext.getSettings().INDENT_CASE_FROM_SWITCH
                               ? 2 * indentSize
                               : indentSize;
            return new ChildAttributes(Indent.getSpaceIndent(spaces), null);
          }
        }
      }
    }

    if (psiParent instanceof GrCaseLabel) {
      return new ChildAttributes(GroovyIndentProcessor.getSwitchCaseIndent(getContext().getSettings()), null);
    }
    if (psiParent instanceof GrCaseSection) {
      return getSwitchIndent((GrCaseSection)psiParent, newChildIndex);
    }

    if (TokenSets.BLOCK_SET.contains(astNode.getElementType()) || GroovyElementTypes.SWITCH_STATEMENT.equals(astNode.getElementType())) {
      return new ChildAttributes(Indent.getNormalIndent(), null);
    }
    if (GroovyElementTypes.CASE_SECTION.equals(astNode.getElementType())) {
      return new ChildAttributes(Indent.getNormalIndent(), null);
    }
    if (psiParent instanceof GrBinaryExpression ||
        psiParent instanceof GrConditionalExpression ||
        psiParent instanceof GrCommandArgumentList ||
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

  private ChildAttributes getSwitchIndent(GrCaseSection psiParent, int newIndex) {
    final GrStatement[] statements = psiParent.getStatements();
    newIndex--;
    for (int i = 0; i < statements.length && i < newIndex; i++) {
      GrStatement statement = statements[i];
      if (statement instanceof GrBreakStatement ||
          statement instanceof GrContinueStatement ||
          statement instanceof GrReturnStatement ||
          statement instanceof GrThrowStatement) {
        return new ChildAttributes(Indent.getNoneIndent(), null);
      }
    }
    final Indent indent = GroovyIndentProcessor.getSwitchCaseIndent(getContext().getSettings());
    return new ChildAttributes(indent, null);
  }


  @Override
  public boolean isIncomplete() {
    return isIncomplete(myNode);
  }

  /**
   * @param node Tree node
   * @return true if node is incomplete
   */
  public static boolean isIncomplete(@NotNull final ASTNode node) {
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
