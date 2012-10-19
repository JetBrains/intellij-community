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

package org.jetbrains.plugins.groovy.formatter.processors;

import com.intellij.formatting.Indent;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.formatter.ClosureBodyBlock;
import org.jetbrains.plugins.groovy.formatter.GroovyBlock;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocComment;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocTag;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrLabel;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrThrowsClause;
import org.jetbrains.plugins.groovy.lang.psi.api.formatter.GrControlStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrConditionalExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrElvisExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameterList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrExtendsClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;

/**
 * @author ilyas
 */
public abstract class GroovyIndentProcessor implements GroovyElementTypes {
  public static final int GDOC_COMMENT_INDENT = 1;
  private static TokenSet GSTRING_TOKENS_INNER = TokenSet.create(mGSTRING_BEGIN,mGSTRING_CONTENT,mGSTRING_END,mDOLLAR);

  /**
   * Calculates indent, based on code style, between parent block and child node
   *
   * @param parent        parent block
   * @param child         child node
   * @return indent
   */
  @NotNull
  public static Indent getChildIndent(@NotNull final GroovyBlock parent, @NotNull final ASTNode child) {
    ASTNode astNode = parent.getNode();
    final PsiElement psiParent = astNode.getPsi();

    // For Groovy file
    if (psiParent instanceof GroovyFileBase) {
      return Indent.getNoneIndent();
    }

    if (psiParent instanceof GrMethod && child.getPsi() instanceof GrParameterList) {
      return Indent.getContinuationIndent();
    }

    if (GSTRING_TOKENS_INNER.contains(child.getElementType()) && mGSTRING_BEGIN != child.getElementType()) {
      return Indent.getAbsoluteNoneIndent();
    }

    if (psiParent instanceof GrListOrMap) {
      if (mLBRACK.equals(child.getElementType()) || mRBRACK.equals(child.getElementType())) {
        return Indent.getNoneIndent();
      } else {
        return Indent.getContinuationWithoutFirstIndent();
      }
    }

    if (child.getElementType() == GroovyElementTypes.PARAMETERS_LIST && parent instanceof ClosureBodyBlock) {
      return Indent.getNoneIndent();
    }

    // For common code block
    if (BLOCK_SET.contains(astNode.getElementType()) &&
        !BLOCK_STATEMENT.equals(astNode.getElementType()) || parent instanceof ClosureBodyBlock) {
      return indentForBlock(psiParent, child);
    }

    if (CASE_SECTION.equals(astNode.getElementType())) {
      return indentForCaseSection(child);
    }

    if (SWITCH_STATEMENT.equals(astNode.getElementType())) {
      return indentForSwitchStatement(psiParent, child);
    }

    if (psiParent instanceof GrLabeledStatement) {
      if (child.getPsi() instanceof GrLabel) {
        CommonCodeStyleSettings.IndentOptions indentOptions = parent.getSettings().getIndentOptions();
        if (indentOptions.LABEL_INDENT_ABSOLUTE) {
          return Indent.getAbsoluteLabelIndent();
        }
        return Indent.getLabelIndent();
      }
    }

    // for control structures
    if (psiParent instanceof GrControlStatement) {
      return getControlIndent(psiParent, child);
    }

    if (psiParent instanceof GrExpression) {
      return getExpressionIndent(psiParent, child);
    }
    if (psiParent instanceof GrVariable && child.getPsi() == ((GrVariable)psiParent).getInitializerGroovy()) {
      return Indent.getNormalIndent();
    }

    //For parameter lists
    if (psiParent instanceof GrParameterList ||
        psiParent instanceof GrExtendsClause ||
        psiParent instanceof GrThrowsClause) {
      if (parent.getIndent() != null) {
        return Indent.getContinuationWithoutFirstIndent();
      }
      return Indent.getNoneIndent();
    }

    // For arguments
    if (psiParent instanceof GrArgumentList) {
      if (child.getElementType() != mLPAREN && child.getElementType() != mRPAREN /*&& child.getElementType() != mCOMMA*/) {
        return Indent.getContinuationWithoutFirstIndent();
      }
    }

    if ((psiParent instanceof GrDocComment &&
        child.getElementType() != mGDOC_COMMENT_START) ||
        psiParent instanceof GrDocTag &&
            child.getElementType() != mGDOC_TAG_NAME) {
      return Indent.getSpaceIndent(GDOC_COMMENT_INDENT);
    }

    if (psiParent instanceof GrNamedArgument && child.getPsi() == ((GrNamedArgument)psiParent).getExpression()) {
      return Indent.getContinuationIndent();
    }

    if (child.getPsi() instanceof GrVariable && psiParent instanceof GrVariableDeclaration) {
      return Indent.getContinuationWithoutFirstIndent();
    }

    return Indent.getNoneIndent();
  }

  /**
   * Returns indent for simple expressions
   *
   * @param psiParent
   * @param child
   * @return
   */
  private static Indent getExpressionIndent(PsiElement psiParent, ASTNode child) {
    // Assignment expression
    if (psiParent instanceof GrAssignmentExpression &&
        child.getPsi().equals(((GrAssignmentExpression) psiParent).getRValue())) {
      return Indent.getNormalIndent();
    }
    // Conditional expression
    if (psiParent instanceof GrConditionalExpression &&
        (child.getPsi().equals(((GrConditionalExpression) psiParent).getThenBranch()) && !(psiParent instanceof GrElvisExpression) ||
            child.getPsi().equals(((GrConditionalExpression) psiParent).getElseBranch()))) {
      return Indent.getNormalIndent();
    }
    // Property selection

    return Indent.getNoneIndent();
  }

  private static Indent getControlIndent(PsiElement parent, ASTNode child) {
    final PsiElement psi = child.getPsi();
    final IElementType type = child.getElementType();
    if (parent instanceof GrIfStatement) {
      final GrIfStatement ifStatement = (GrIfStatement)parent;
      if (!BLOCK_SET.contains(type)) {
        if (psi.equals(ifStatement.getThenBranch())) {
          return Indent.getNormalIndent();
        }
        if (psi.equals(ifStatement.getElseBranch())) {
          if (getGroovySettings(parent).SPECIAL_ELSE_IF_TREATMENT && psi instanceof GrIfStatement) {
            return Indent.getNoneIndent();
          }
          return Indent.getNormalIndent();
        }
      }
      if (psi.equals(ifStatement.getCondition())) {
        return Indent.getContinuationWithoutFirstIndent();
      }
    }
    if (parent instanceof GrWhileStatement) {
      if (psi.equals(((GrWhileStatement) parent).getBody()) &&
          !BLOCK_SET.contains(type)) {
        return Indent.getNormalIndent();
      }
      if (psi.equals(((GrWhileStatement) parent).getCondition())) {
        return Indent.getContinuationWithoutFirstIndent();
      }
    }
    if (parent instanceof GrSynchronizedStatement) {
      if (psi.equals(((GrSynchronizedStatement) parent).getMonitor())) {
        return Indent.getContinuationWithoutFirstIndent();
      }
    }
    if (parent instanceof GrForStatement) {
      if (psi.equals(((GrForStatement) parent).getBody()) &&
          !BLOCK_SET.contains(type)) {
        return Indent.getNormalIndent();
      }
      if (psi.equals(((GrForStatement) parent).getClause())) {
        return Indent.getContinuationWithoutFirstIndent();
      }
    }
    return Indent.getNoneIndent();
  }

  private static CommonCodeStyleSettings getGroovySettings(PsiElement parent) {
    return CodeStyleSettingsManager.getSettings(parent.getProject()).getCommonSettings(GroovyFileType.GROOVY_LANGUAGE);
  }

  /**
   * Indent for common block
   *
   * @param psiBlock
   * @param child
   * @return
   */
  private static Indent indentForBlock(PsiElement psiBlock, ASTNode child) {
    // Common case
    if (mLCURLY.equals(child.getElementType()) ||
        mRCURLY.equals(child.getElementType())) {
      return Indent.getNoneIndent();
    }
    return Indent.getNormalIndent();


  }

  private static Indent indentForCaseSection(ASTNode child) {
    if (CASE_LABEL.equals(child.getElementType())) {
      return Indent.getNoneIndent();
    }
    return Indent.getNormalIndent();
  }

  private static Indent indentForSwitchStatement(PsiElement psiParent, ASTNode child) {
    if (CASE_SECTION.equals(child.getElementType())) {
      return getSwitchCaseIndent(psiParent);
    }
    return Indent.getNoneIndent();
  }

  public static Indent getSwitchCaseIndent(PsiElement psiParent) {
    if (getGroovySettings(psiParent).INDENT_CASE_FROM_SWITCH) {
        return Indent.getNormalIndent();
      }
    return Indent.getNoneIndent();
  }
}

