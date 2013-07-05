/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.formatter.blocks.ClosureBodyBlock;
import org.jetbrains.plugins.groovy.formatter.blocks.GrLabelBlock;
import org.jetbrains.plugins.groovy.formatter.blocks.GroovyBlock;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocComment;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocMethodParams;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocTag;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrThrowsClause;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotation;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotationArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.GrAssertStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.clauses.GrCaseSection;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrConditionalExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrParenthesizedExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameterList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrExtendsClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrImplementsClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinitionBody;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;

import static org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes.*;

/**
 * @author ilyas
 */
public class GroovyIndentProcessor extends GroovyElementVisitor {
  public static final int GDOC_COMMENT_INDENT = 1;
  private static final TokenSet GSTRING_TOKENS_INNER = TokenSet.create(mGSTRING_CONTENT, mGSTRING_END, mDOLLAR);

  private Indent myResult = null;

  private IElementType myChildType;
  private GroovyBlock myBlock;
  private PsiElement myChild;

  /**
   * Calculates indent, based on code style, between parent block and child node
   *
   * @param parentBlock parent block
   * @param child       child node
   * @return indent
   */
  @NotNull
  public Indent getChildIndent(@NotNull final GroovyBlock parentBlock, @NotNull final ASTNode child) {
    myChildType = child.getElementType();
    if (parentBlock instanceof ClosureBodyBlock) {
      if (myChildType == PARAMETERS_LIST) {
        return Indent.getNoneIndent();
      }
      else if (myChildType != mLCURLY && myChildType != mRCURLY) {
        return Indent.getNormalIndent();
      }
    }
    if (parentBlock instanceof GrLabelBlock) {
      return myChildType == LABELED_STATEMENT
             ? Indent.getNoneIndent()
             : Indent.getLabelIndent();

    }

    if (GSTRING_TOKENS_INNER.contains(myChildType)) {
      return Indent.getAbsoluteNoneIndent();
    }

    final PsiElement parent = parentBlock.getNode().getPsi();
    if (parent instanceof GroovyPsiElement) {
      myBlock = parentBlock;
      myChild = child.getPsi();
      ((GroovyPsiElement)parent).accept(this);
      if (myResult != null) return myResult;
    }

    return Indent.getNoneIndent();
  }

  @Override
  public void visitAssertStatement(GrAssertStatement assertStatement) {
    if (myChildType != GroovyTokenTypes.kASSERT) {
      myResult = Indent.getContinuationIndent();
    }
  }

  @Override
  public void visitListOrMap(GrListOrMap listOrMap) {
    if (myChildType != mLBRACK && myChildType != mRBRACK) {
      myResult = Indent.getContinuationWithoutFirstIndent();
    }
  }

  @Override
  public void visitCaseSection(GrCaseSection caseSection) {
    if (myChildType != CASE_LABEL) {
      myResult = Indent.getNormalIndent();
    }
  }

  @Override
  public void visitSwitchStatement(GrSwitchStatement switchStatement) {
    if (myChildType == CASE_SECTION) {
      myResult = getSwitchCaseIndent(getGroovySettings());
    }
  }

  @Override
  public void visitLabeledStatement(GrLabeledStatement labeledStatement) {
    if (myChildType == mIDENT) {
      CommonCodeStyleSettings.IndentOptions indentOptions = myBlock.getContext().getSettings().getIndentOptions();
      if (indentOptions != null && indentOptions.LABEL_INDENT_ABSOLUTE) {
        myResult = Indent.getAbsoluteLabelIndent();
      }
      else if (!myBlock.getContext().getGroovySettings().INDENT_LABEL_BLOCKS) {
        myResult = Indent.getLabelIndent();
      }
    }
    else {
      if (myBlock.getContext().getGroovySettings().INDENT_LABEL_BLOCKS) {
        myResult = Indent.getLabelIndent();
      }
    }
  }

  @Override
  public void visitAnnotation(GrAnnotation annotation) {
    if (myChildType == ANNOTATION_ARGUMENTS) {
      myResult = Indent.getContinuationIndent();
    }
    else {
      myResult = Indent.getNoneIndent();
    }
  }

  @Override
  public void visitArgumentList(GrArgumentList list) {
    if (myChildType != mLPAREN && myChildType != mRPAREN) {
      myResult = Indent.getContinuationWithoutFirstIndent();
    }
  }

  @Override
  public void visitIfStatement(GrIfStatement ifStatement) {
    if (TokenSets.BLOCK_SET.contains(myChildType)) {
      if (myChild == ifStatement.getCondition()) {
        myResult = Indent.getContinuationWithoutFirstIndent();
      }
    }
    else if (myChild == ifStatement.getThenBranch()) {
      myResult = Indent.getNormalIndent();
    }
    else if (myChild == ifStatement.getElseBranch()) {
      if (getGroovySettings().SPECIAL_ELSE_IF_TREATMENT && myChildType == IF_STATEMENT) {
        myResult = Indent.getNoneIndent();
      }
      else {
        myResult = Indent.getNormalIndent();
      }
    }
  }

  @Override
  public void visitAnnotationArgumentList(GrAnnotationArgumentList annotationArgumentList) {
    if (myChildType == mLPAREN || myChildType == mRPAREN) {
      myResult = Indent.getNoneIndent();
    }
    else {
      myResult = Indent.getContinuationIndent();
    }
  }

  @Override
  public void visitNamedArgument(GrNamedArgument argument) {
    if (myChild == argument.getExpression()) {
      myResult = Indent.getContinuationIndent();
    }
  }

  @Override
  public void visitVariable(GrVariable variable) {
    if (myChild == variable.getInitializerGroovy()) {
      myResult = Indent.getContinuationIndent();
    }
  }

  @Override
  public void visitDocComment(GrDocComment comment) {
    if (myChildType != mGDOC_COMMENT_START) {
      myResult = Indent.getSpaceIndent(GDOC_COMMENT_INDENT);
    }
  }

  @Override
  public void visitVariableDeclaration(GrVariableDeclaration variableDeclaration) {
    if (myChild instanceof GrVariable) {
      myResult = Indent.getContinuationWithoutFirstIndent();
    }
  }

  @Override
  public void visitDocTag(GrDocTag docTag) {
    if (myChildType != mGDOC_TAG_NAME) {
      myResult = Indent.getSpaceIndent(GDOC_COMMENT_INDENT);
    }
  }

  @Override
  public void visitConditionalExpression(GrConditionalExpression expression) {
      myResult = Indent.getContinuationWithoutFirstIndent();
  }

  @Override
  public void visitAssignmentExpression(GrAssignmentExpression expression) {
    myResult = Indent.getContinuationWithoutFirstIndent();
  }

  @Override
  public void visitThrowsClause(GrThrowsClause throwsClause) {
    myResult = Indent.getContinuationWithoutFirstIndent();
  }

  @Override
  public void visitImplementsClause(GrImplementsClause implementsClause) {
    myResult = Indent.getContinuationWithoutFirstIndent();
  }

  @Override
  public void visitDocMethodParameterList(GrDocMethodParams params) {
    myResult = Indent.getContinuationWithoutFirstIndent();
  }

  @Override
  public void visitExtendsClause(GrExtendsClause extendsClause) {
    myResult = Indent.getContinuationWithoutFirstIndent();
  }

  @Override
  public void visitFile(GroovyFileBase file) {
    myResult = Indent.getNoneIndent();
  }

  @Override
  public void visitMethod(GrMethod method) {
    if (myChildType == PARAMETERS_LIST) {
      myResult = Indent.getContinuationIndent();
    }
    else if (myChildType == THROW_CLAUSE) {
      myResult = getGroovySettings().ALIGN_THROWS_KEYWORD ? Indent.getNoneIndent() : Indent.getContinuationIndent();
    }
  }

  @Override
  public void visitTypeDefinition(GrTypeDefinition typeDefinition) {
    if (myChildType == EXTENDS_CLAUSE || myChildType == IMPLEMENTS_CLAUSE) {
      myResult = Indent.getContinuationIndent();
    }
  }

  @Override
  public void visitTypeDefinitionBody(GrTypeDefinitionBody typeDefinitionBody) {
    if (myChildType != mLCURLY && myChildType != mRCURLY) {
      myResult = Indent.getNormalIndent();
    }
  }

  @Override
  public void visitClosure(GrClosableBlock closure) {
    if (myChildType != mLCURLY && myChildType != mRCURLY) {
      myResult = Indent.getNormalIndent();
    }
  }

  @Override
  public void visitOpenBlock(GrOpenBlock block) {
    final IElementType type = block.getNode().getElementType();
    if (type != OPEN_BLOCK && type != CONSTRUCTOR_BODY) return;

    if (myChildType != mLCURLY && myChildType != mRCURLY) {
      myResult = Indent.getNormalIndent();
    }
  }

  @Override
  public void visitWhileStatement(GrWhileStatement whileStatement) {
    if (myChild == (whileStatement).getBody() && !TokenSets.BLOCK_SET.contains(myChildType)) {
      myResult = Indent.getNormalIndent();
    }
    else if (myChild == whileStatement.getCondition()) {
      myResult = Indent.getContinuationWithoutFirstIndent();
    }
  }

  @Override
  public void visitSynchronizedStatement(GrSynchronizedStatement synchronizedStatement) {
    if (myChild == synchronizedStatement.getMonitor()) {
      myResult = Indent.getContinuationWithoutFirstIndent();
    }
  }

  @Override
  public void visitForStatement(GrForStatement forStatement) {
    if (myChild == forStatement.getBody() && !TokenSets.BLOCK_SET.contains(myChildType)) {
      myResult = Indent.getNormalIndent();
    }
    else if (myChild == forStatement.getClause()) {
      myResult = Indent.getContinuationWithoutFirstIndent();
    }
  }

  private CommonCodeStyleSettings getGroovySettings() {
    return myBlock.getContext().getSettings();
  }

  @Override
  public void visitParenthesizedExpression(GrParenthesizedExpression expression) {
    if (myChildType == mLPAREN || myChildType == mRPAREN) {
      myResult = Indent.getNoneIndent();
    }
    else {
      myResult = Indent.getContinuationIndent();
    }
  }

  public static Indent getSwitchCaseIndent(final CommonCodeStyleSettings settings) {
    if (settings.INDENT_CASE_FROM_SWITCH) {
      return Indent.getNormalIndent();
    }
    else {
      return Indent.getNoneIndent();
    }
  }

  @Override
  public void visitParameterList(GrParameterList parameterList) {
    myResult = Indent.getContinuationWithoutFirstIndent();
  }

}

