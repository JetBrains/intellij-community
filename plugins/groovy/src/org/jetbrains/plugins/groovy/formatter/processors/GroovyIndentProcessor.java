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
import org.jetbrains.plugins.groovy.lang.groovydoc.lexer.GroovyDocTokenTypes;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocComment;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocMethodParams;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocTag;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrThrowsClause;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.GrAssertStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.clauses.GrCaseSection;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.clauses.GrForClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.clauses.GrForInClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameterList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrExtendsClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrImplementsClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinitionBody;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrAnnotationMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeParameterList;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrWildcardTypeArgument;

/**
 * @author ilyas
 */
public class GroovyIndentProcessor extends GroovyElementVisitor {
  public static final int GDOC_COMMENT_INDENT = 1;
  private static final TokenSet GSTRING_TOKENS_INNER = TokenSet.create(GroovyTokenTypes.mGSTRING_CONTENT, GroovyTokenTypes.mGSTRING_END,
                                                                       GroovyTokenTypes.mDOLLAR);

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
      if (myChildType == GroovyElementTypes.PARAMETERS_LIST) {
        return Indent.getNoneIndent();
      }
      else if (myChildType != GroovyTokenTypes.mLCURLY && myChildType != GroovyTokenTypes.mRCURLY) {
        return Indent.getNormalIndent();
      }
    }
    if (parentBlock instanceof GrLabelBlock) {
      ASTNode first = parentBlock.getNode().getFirstChildNode();
      return child == first
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
  public void visitAnnotationArrayInitializer(GrAnnotationArrayInitializer arrayInitializer) {
    if (myChildType != GroovyTokenTypes.mLBRACK && myChildType != GroovyTokenTypes.mRBRACK) {
      myResult = Indent.getContinuationWithoutFirstIndent();
    }
  }

  @Override
  public void visitListOrMap(GrListOrMap listOrMap) {
    if (myChildType != GroovyTokenTypes.mLBRACK && myChildType != GroovyTokenTypes.mRBRACK) {
      myResult = Indent.getContinuationWithoutFirstIndent();
    }
  }

  @Override
  public void visitCaseSection(GrCaseSection caseSection) {
    if (myChildType != GroovyElementTypes.CASE_LABEL) {
      myResult = Indent.getNormalIndent();
    }
  }

  @Override
  public void visitSwitchStatement(GrSwitchStatement switchStatement) {
    if (myChildType == GroovyElementTypes.CASE_SECTION) {
      myResult = getSwitchCaseIndent(getGroovySettings());
    }
  }

  @Override
  public void visitLabeledStatement(GrLabeledStatement labeledStatement) {
    if (myChildType == GroovyTokenTypes.mIDENT) {
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
    if (myChildType == GroovyElementTypes.ANNOTATION_ARGUMENTS) {
      myResult = Indent.getContinuationIndent();
    }
    else {
      myResult = Indent.getNoneIndent();
    }
  }

  @Override
  public void visitArgumentList(GrArgumentList list) {
    if (myChildType != GroovyTokenTypes.mLPAREN && myChildType != GroovyTokenTypes.mRPAREN) {
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
      if (getGroovySettings().SPECIAL_ELSE_IF_TREATMENT && myChildType == GroovyElementTypes.IF_STATEMENT) {
        myResult = Indent.getNoneIndent();
      }
      else {
        myResult = Indent.getNormalIndent();
      }
    }
  }

  @Override
  public void visitAnnotationArgumentList(GrAnnotationArgumentList annotationArgumentList) {
    if (myChildType == GroovyTokenTypes.mLPAREN || myChildType == GroovyTokenTypes.mRPAREN) {
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
    myResult = Indent.getContinuationWithoutFirstIndent();
  }

  @Override
  public void visitDocComment(GrDocComment comment) {
    if (myChildType != GroovyDocTokenTypes.mGDOC_COMMENT_START) {
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
    if (myChildType != GroovyDocTokenTypes.mGDOC_TAG_NAME) {
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
    if (myChildType == GroovyElementTypes.PARAMETERS_LIST) {
      myResult = Indent.getContinuationIndent();
    }
    else if (myChildType == GroovyElementTypes.THROW_CLAUSE) {
      myResult = getGroovySettings().ALIGN_THROWS_KEYWORD ? Indent.getNoneIndent() : Indent.getContinuationIndent();
    }
  }

  @Override
  public void visitTypeDefinition(GrTypeDefinition typeDefinition) {
    if (myChildType == GroovyElementTypes.EXTENDS_CLAUSE || myChildType == GroovyElementTypes.IMPLEMENTS_CLAUSE) {
      myResult = Indent.getContinuationIndent();
    }
  }

  @Override
  public void visitTypeDefinitionBody(GrTypeDefinitionBody typeDefinitionBody) {
    if (myChildType != GroovyTokenTypes.mLCURLY && myChildType != GroovyTokenTypes.mRCURLY) {
      myResult = Indent.getNormalIndent();
    }
  }

  @Override
  public void visitClosure(GrClosableBlock closure) {
    if (myChildType != GroovyTokenTypes.mLCURLY && myChildType != GroovyTokenTypes.mRCURLY) {
      myResult = Indent.getNormalIndent();
    }
  }

  @Override
  public void visitOpenBlock(GrOpenBlock block) {
    final IElementType type = block.getNode().getElementType();
    if (type != GroovyElementTypes.OPEN_BLOCK && type != GroovyElementTypes.CONSTRUCTOR_BODY) return;

    if (myChildType != GroovyTokenTypes.mLCURLY && myChildType != GroovyTokenTypes.mRCURLY) {
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
    if (myChildType == GroovyTokenTypes.mLPAREN || myChildType == GroovyTokenTypes.mRPAREN) {
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

  @Override
  public void visitArrayDeclaration(GrArrayDeclaration arrayDeclaration) {
    myResult = Indent.getContinuationWithoutFirstIndent();
  }

  @Override
  public void visitExpression(GrExpression expression) {
    myResult = Indent.getContinuationWithoutFirstIndent();
  }

  @Override
  public void visitTypeArgumentList(GrTypeArgumentList typeArgumentList) {
    myResult = Indent.getContinuationWithoutFirstIndent();
  }

  @Override
  public void visitCodeReferenceElement(GrCodeReferenceElement refElement) {
    myResult = Indent.getContinuationWithoutFirstIndent();
  }

  @Override
  public void visitWildcardTypeArgument(GrWildcardTypeArgument wildcardTypeArgument) {
    myResult = Indent.getContinuationWithoutFirstIndent();
  }

  @Override
  public void visitAnnotationMethod(GrAnnotationMethod annotationMethod) {
    if (myChild instanceof GrAnnotationMemberValue) {
      myResult = Indent.getContinuationIndent();
    }
    else {
      super.visitAnnotationMethod(annotationMethod);
    }
  }

  @Override
  public void visitAnnotationNameValuePair(GrAnnotationNameValuePair nameValuePair) {
    myResult = Indent.getContinuationWithoutFirstIndent();
  }

  @Override
  public void visitForInClause(GrForInClause forInClause) {
    myResult = Indent.getContinuationWithoutFirstIndent();
  }

  @Override
  public void visitForClause(GrForClause forClause) {
    myResult = Indent.getContinuationWithoutFirstIndent();
  }

  @Override
  public void visitCatchClause(GrCatchClause catchClause) {
    if (myChild == catchClause.getBody()) {
      myResult = Indent.getNoneIndent();
    }
    else {
      myResult = Indent.getContinuationWithoutFirstIndent();
    }
  }

  @Override
  public void visitTypeParameterList(GrTypeParameterList list) {
    myResult = Indent.getContinuationWithoutFirstIndent();
  }



}

