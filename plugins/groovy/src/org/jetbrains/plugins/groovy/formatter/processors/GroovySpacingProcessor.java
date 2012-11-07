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

package org.jetbrains.plugins.groovy.formatter.processors;

import com.intellij.formatting.Spacing;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.formatter.FormatterUtil;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.tree.CompositeElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.codeStyle.GroovyCodeStyleSettings;
import org.jetbrains.plugins.groovy.formatter.GeeseUtil;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.*;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotation;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotationArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotationArrayInitializer;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.clauses.GrForInClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrStringInjection;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinitionBody;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrAnnotationMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeParameterList;
import org.jetbrains.plugins.groovy.lang.psi.util.GrStringUtil;

import static org.jetbrains.plugins.groovy.GroovyFileType.GROOVY_LANGUAGE;
import static org.jetbrains.plugins.groovy.formatter.models.spacing.SpacingTokens.*;
import static org.jetbrains.plugins.groovy.formatter.models.spacing.SpacingTokens.mCOMMA;
import static org.jetbrains.plugins.groovy.formatter.models.spacing.SpacingTokens.mELVIS;
import static org.jetbrains.plugins.groovy.formatter.models.spacing.SpacingTokens.mQUESTION;
import static org.jetbrains.plugins.groovy.lang.groovydoc.lexer.GroovyDocTokenTypes.mGDOC_ASTERISKS;
import static org.jetbrains.plugins.groovy.lang.groovydoc.lexer.GroovyDocTokenTypes.mGDOC_INLINE_TAG_END;
import static org.jetbrains.plugins.groovy.lang.groovydoc.lexer.GroovyDocTokenTypes.mGDOC_INLINE_TAG_START;
import static org.jetbrains.plugins.groovy.lang.groovydoc.lexer.GroovyDocTokenTypes.mGDOC_TAG_VALUE_COMMA;
import static org.jetbrains.plugins.groovy.lang.groovydoc.lexer.GroovyDocTokenTypes.mGDOC_TAG_VALUE_LPAREN;
import static org.jetbrains.plugins.groovy.lang.groovydoc.lexer.GroovyDocTokenTypes.mGDOC_TAG_VALUE_RPAREN;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.GDOC_TAG;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.kELSE;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.kNEW;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.kSWITCH;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.kSYNCHRONIZED;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.mCLOSABLE_BLOCK_OP;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.mCOLON;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.mGT;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.mLBRACK;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.mLCURLY;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.mLPAREN;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.mLT;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.mNLS;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.mRBRACK;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.mRCURLY;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.mRPAREN;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.mSL_COMMENT;
import static org.jetbrains.plugins.groovy.lang.lexer.TokenSets.COMMENT_SET;

/**
 * @author ilyas
 */
public class GroovySpacingProcessor extends GroovyElementVisitor {
  private static final Logger LOG = Logger.getInstance(GroovySpacingProcessor.class);

  private PsiElement myParent;
  private final CommonCodeStyleSettings mySettings;

  private Spacing myResult;
  private ASTNode myChild1;
  private ASTNode myChild2;
  private IElementType myType1;
  private IElementType myType2;
  private GroovyCodeStyleSettings myGroovySettings;

  public GroovySpacingProcessor(ASTNode node, CommonCodeStyleSettings settings, GroovyCodeStyleSettings groovySettings) {
    mySettings = settings;
    myGroovySettings = groovySettings;

    _init(node);

    if (myChild1 == null || myChild2 == null) {
      return;
    }

    PsiElement psi1 = myChild1.getPsi();
    PsiElement psi2 = myChild2.getPsi();
    if (psi1 == null || psi2 == null) return;
    if (psi1.getLanguage() != GROOVY_LANGUAGE || psi2.getLanguage() != GROOVY_LANGUAGE) {
      return;
    }

    ASTNode prev = getPrevElementType(myChild2);
    if (prev != null && prev.getElementType() == mNLS) {
      prev = getPrevElementType(prev);
    }
    if (mySettings.KEEP_FIRST_COLUMN_COMMENT && COMMENT_SET.contains(myType2)) {
      if (myType1 != IMPORT_STATEMENT) {
        myResult = Spacing.createKeepingFirstColumnSpacing(0, Integer.MAX_VALUE, true, 1);
      }
      return;
    }
    if (prev != null && prev.getElementType() == mSL_COMMENT) {
      myResult = Spacing.createSpacing(0, Integer.MAX_VALUE, 1, mySettings.KEEP_LINE_BREAKS, mySettings.KEEP_BLANK_LINES_IN_CODE);
      return;
    }


    if (myParent instanceof GroovyPsiElement) {
      ((GroovyPsiElement) myParent).accept(this);
    }
  }

  private void _init(final ASTNode child) {
    if (child != null) {
      ASTNode treePrev = child.getTreePrev();
      while (treePrev != null && isWhiteSpace(treePrev)) {
        treePrev = treePrev.getTreePrev();
      }
      if (treePrev == null) {
        _init(child.getTreeParent());
      }
      else {
        myChild2 = child;
        myType2 = myChild2.getElementType();

        myChild1 = treePrev;
        myType1 = myChild1.getElementType();
        final CompositeElement parent = (CompositeElement)treePrev.getTreeParent();
        myParent = SourceTreeToPsiMap.treeElementToPsi(parent);
      }
    }
  }

  @Override
  public void visitLiteralExpression(GrLiteral literal) {
    createSpaceInCode(false);
  }

  @Override
  public void visitGStringInjection(GrStringInjection injection) {
    createSpaceInCode(false);
  }

  @Override
  public void visitAnnotation(GrAnnotation annotation) {
    if (myType2 == ANNOTATION_ARGUMENTS) {
      createSpaceInCode(mySettings.SPACE_BEFORE_ANOTATION_PARAMETER_LIST);
    }
  }

  @Override
  public void visitAnnotationArgumentList(GrAnnotationArgumentList annotationArgumentList) {
    if (myType1 == mLPAREN || myType2 == mRPAREN) {
      createSpaceInCode(mySettings.SPACE_WITHIN_ANNOTATION_PARENTHESES);
    }
  }

  public void visitArgumentList(GrArgumentList list) {
    if (myType1 == mLBRACK || myType2 == mRBRACK) {
      createSpaceInCode(mySettings.SPACE_WITHIN_BRACKETS);
    }
    else if (myType1 == mLPAREN || myType2 == mRPAREN) {
      if (list.getAllArguments().length > 0) {
        createSpaceInCode(mySettings.SPACE_WITHIN_METHOD_CALL_PARENTHESES);
      }
      else {
        createSpaceInCode(mySettings.SPACE_WITHIN_EMPTY_METHOD_CALL_PARENTHESES);
      }
    }
  }

  @Override
  public void visitConditionalExpression(GrConditionalExpression expression) {
    if (myType2 == mQUESTION) {
      createSpaceInCode(mySettings.SPACE_BEFORE_QUEST);
    }
    else if (myType1 == mQUESTION) {
      createSpaceInCode(mySettings.SPACE_AFTER_QUEST);
    }
    else if (myType2 == mCOLON) {
      createSpaceInCode(mySettings.SPACE_BEFORE_COLON);
    }
    else if (myType1 == mCOLON) {
      createSpaceInCode(mySettings.SPACE_AFTER_COLON);
    }
  }

  @Override
  public void visitElvisExpression(GrElvisExpression expression) {
    if (myType1 == mELVIS) {
      createSpaceInCode(mySettings.SPACE_AFTER_COLON);
    }
    else if (myType2 == mELVIS) {
      createSpaceInCode(mySettings.SPACE_BEFORE_QUEST);
    }
  }

  @Override
  public void visitMethodCallExpression(GrMethodCallExpression methodCallExpression) {
    if (myType2 == ARGUMENTS) createSpaceInCode(mySettings.SPACE_BEFORE_METHOD_CALL_PARENTHESES);
    if (myType2 == CLOSABLE_BLOCK) createSpaceInCode(myGroovySettings.SPACE_BEFORE_CLOSURE_LBRACE);
  }

  public void visitClosure(GrClosableBlock closure) {
    ASTNode rBraceAtTheEnd = GeeseUtil.getClosureRBraceAtTheEnd(myChild1);
    if (myGroovySettings.USE_FLYING_GEESE_BRACES && myType2 == mRCURLY && rBraceAtTheEnd != null) {
      String text = rBraceAtTheEnd.getTreeParent().getText();
      if (text.indexOf('\n') < 0) {
        /* the case:
       foo {
         bar {print x}<we are here>}
        */
        myResult = Spacing.createSpacing(1, 1, 1, false, 1);
      }
      else {
        myResult = Spacing.createSpacing(0, 0, 0, true, 100, 0);
      }
    }
    else if (myType1 == mLCURLY && myType2 == mRCURLY) {  //empty closure
      myResult = Spacing.createSpacing(0, 0, 0, mySettings.KEEP_LINE_BREAKS, mySettings.KEEP_BLANK_LINES_IN_CODE);
    }
    else if (closure.getParameters().length == 0 && (myType1 == mLCURLY && myType2 != PARAMETERS_LIST && myType2 != mCLOSABLE_BLOCK_OP || myType2 == mRCURLY)) { //spaces between statements

      boolean spacesWithinBraces = closure.getParent() instanceof GrStringInjection
                  ? myGroovySettings.SPACE_WITHIN_GSTRING_INJECTION_BRACES
                  : mySettings.SPACE_WITHIN_BRACES;
      int minSpaces = spacesWithinBraces ? 1 : 0;
      myResult = Spacing.createDependentLFSpacing(minSpaces, 1, closure.getTextRange(), mySettings.KEEP_LINE_BREAKS,
                                                  mySettings.KEEP_BLANK_LINES_IN_CODE);
    }
    else if (myType1 == mCLOSABLE_BLOCK_OP) {
      myResult = GroovySpacingProcessorBasic.createDependentSpacingForClosure(mySettings, myGroovySettings, closure, true);
    }
  }

  public void visitOpenBlock(GrOpenBlock block) {
    if (block.getParent() instanceof GrBlockStatement) {
      if (myType1 == mLCURLY || myType2 == mRCURLY) {
        myResult = Spacing.createSpacing(1, 1, 1, mySettings.KEEP_LINE_BREAKS, mySettings.KEEP_BLANK_LINES_IN_CODE);
      }
    }
    else if (myType1 == mLCURLY && myType2 == mRCURLY) {
      myResult = Spacing.createSpacing(0, 0, 0, mySettings.KEEP_LINE_BREAKS, mySettings.KEEP_BLANK_LINES_IN_CODE);
    }
    else if (myType1 == mLCURLY && !GrStringUtil.isMultilineStringElement(myChild2) ||
             myType2 == mRCURLY && !GrStringUtil.isMultilineStringElement(myChild1)) {
      final int spaceWithinBraces = mySettings.SPACE_WITHIN_BRACES ? 1 : 0;
      final TextRange range = block.getTextRange();
      myResult = Spacing.createDependentLFSpacing(spaceWithinBraces, 1, range, mySettings.KEEP_LINE_BREAKS, mySettings.KEEP_BLANK_LINES_IN_CODE);
    }
  }

  public void visitNewExpression(GrNewExpression newExpression) {
    if (myType1 == kNEW) {
      createSpaceInCode(true);
    }
    else if (myType2 == ARGUMENTS) {
      createSpaceInCode(mySettings.SPACE_BEFORE_METHOD_CALL_PARENTHESES);
    }
  }

  public void visitTypeDefinition(GrTypeDefinition typeDefinition) {
    if (myType2 == CLASS_BODY) {
      PsiElement nameIdentifier = typeDefinition.getNameIdentifierGroovy();
      int dependenceStart = nameIdentifier.getTextRange().getStartOffset();
      createSpaceBeforeLBrace(mySettings.SPACE_BEFORE_CLASS_LBRACE, mySettings.CLASS_BRACE_STYLE, new TextRange(dependenceStart, myChild1.getTextRange().getEndOffset()), false);
    }
  }

  public void visitTypeDefinitionBody(GrTypeDefinitionBody typeDefinitionBody) {
    if (myType1 == mLCURLY && myType2 == mRCURLY) {
      myResult = Spacing.createSpacing(0, 0, 0, mySettings.KEEP_LINE_BREAKS, mySettings.KEEP_BLANK_LINES_BEFORE_RBRACE);
    } else if (myType1 == mLCURLY) {
      myResult = Spacing.createSpacing(0, 0, mySettings.BLANK_LINES_AFTER_CLASS_HEADER + 1,
                                       mySettings.KEEP_LINE_BREAKS, mySettings.KEEP_BLANK_LINES_IN_DECLARATIONS);
    } else if (myType2 == mRCURLY) {
      myResult = Spacing.createSpacing(0, Integer.MAX_VALUE, 1, mySettings.KEEP_LINE_BREAKS, mySettings.KEEP_BLANK_LINES_BEFORE_RBRACE);
    }
  }

  @Override
  public void visitTypeArgumentList(GrTypeArgumentList typeArgumentList) {
    if (myType1 == mLT || myType2 == mGT) {
      createSpaceProperty(false, true, 1);
    }
  }

  @Override
  public void visitTypeParameterList(GrTypeParameterList list) {
    if (myType1 == mCOMMA) {
      createSpaceInCode(mySettings.SPACE_AFTER_COMMA_IN_TYPE_ARGUMENTS);
    }
  }

  @Override
  public void visitForInClause(GrForInClause forInClause) {
    if (myType1 == PARAMETER && myType2 == mCOLON) {
      createSpaceInCode(true);
    }
  }

  @Override
  public void visitCastExpression(GrTypeCastExpression typeCastExpression) {
    if (LEFT_BRACES.contains(myType1) || RIGHT_BRACES.contains(myType2)) {
      createSpaceInCode(mySettings.SPACE_WITHIN_CAST_PARENTHESES);
    }
    else if (myType1 == mRPAREN) {
      createSpaceInCode(mySettings.SPACE_AFTER_TYPE_CAST);
    }
  }

  @Override
  public void visitMethod(GrMethod method) {
    if (myType2 == mLPAREN) {
      createSpaceInCode(mySettings.SPACE_BEFORE_METHOD_PARENTHESES);
    }
    else if (myType1 == mLPAREN || myType2 == mRPAREN) {
      createSpaceInCode(mySettings.SPACE_WITHIN_METHOD_PARENTHESES);
    }
    else if (myType1 == mRPAREN && myType2 == THROW_CLAUSE) {
      createSpaceInCode(true);
    }
    else if (isOpenBlock(myType2)) {
      PsiElement methodName = method.getNameIdentifier();
      int dependencyStart = methodName == null ? myParent.getTextRange().getStartOffset() : methodName.getTextRange().getStartOffset();
      createSpaceBeforeLBrace(mySettings.SPACE_BEFORE_METHOD_LBRACE, mySettings.METHOD_BRACE_STYLE,
                              new TextRange(dependencyStart, myChild1.getTextRange().getEndOffset()),
                              mySettings.KEEP_SIMPLE_METHODS_IN_ONE_LINE);
    }
    else if (myType1 == MODIFIERS) {
      processModifierList(myChild1);
    }
    else if (COMMENT_SET.contains(myType1) &&
             (myType2 == MODIFIERS || myType2 == REFERENCE_ELEMENT)) {
      myResult = Spacing.createSpacing(0, 0, 1, mySettings.KEEP_LINE_BREAKS, 0);
    }
  }

  @Override
  public void visitAnnotationMethod(GrAnnotationMethod annotationMethod) {
    if (myType2 == mLPAREN) {
      createSpaceInCode(mySettings.SPACE_BEFORE_METHOD_PARENTHESES);
    }
  }

  public void visitDocMethodReference(GrDocMethodReference reference) {
    visitDocMember();
  }

  public void visitDocFieldReference(GrDocFieldReference reference) {
    visitDocMember();
  }

  private void visitDocMember() {
    myResult = Spacing.createSpacing(0, 0, 0, false, 0);
  }

  public void visitDocMethodParameterList(GrDocMethodParams params) {
    if (myType1 == mGDOC_TAG_VALUE_LPAREN || myType2 == mGDOC_TAG_VALUE_RPAREN) {
      myResult = Spacing.createSpacing(0, 0, 0, false, 0);
      return;
    }
    if (myType2 == mGDOC_TAG_VALUE_COMMA) {
      myResult = Spacing.createSpacing(0, 0, 0, false, 0);
      return;
    }
    createSpaceInCode(true);
  }

  public void visitDocMethodParameter(GrDocMethodParameter parameter) {
    if (myChild1.getTreePrev() == null) {
      createSpaceInCode(true);
    }
  }

  public void visitWhileStatement(GrWhileStatement statement) {
    if (myType2 == mLPAREN) {
      createSpaceInCode(mySettings.SPACE_BEFORE_WHILE_PARENTHESES);
    }
    else if (myType1 == mLPAREN || myType2 == mRPAREN) {
      createSpaceInCode(mySettings.SPACE_WITHIN_WHILE_PARENTHESES);
    }
    else if (myChild2.getPsi() instanceof GrBlockStatement) {
      createSpaceBeforeLBrace(mySettings.SPACE_BEFORE_WHILE_LBRACE, mySettings.BRACE_STYLE,
                              new TextRange(myParent.getTextRange().getStartOffset(), myChild1.getTextRange().getEndOffset()),
                              mySettings.KEEP_SIMPLE_BLOCKS_IN_ONE_LINE);
    }
    else {
      createSpacingBeforeElementInsideControlStatement();
    }
  }

  public void visitCatchClause(GrCatchClause catchClause) {
    if (isOpenBlock(myType2)) {
      createSpaceBeforeLBrace(mySettings.SPACE_BEFORE_CATCH_LBRACE, mySettings.BRACE_STYLE, null,
                              mySettings.KEEP_SIMPLE_BLOCKS_IN_ONE_LINE);
    }
    else if (myType2 == mLPAREN) {
      createSpaceInCode(mySettings.SPACE_BEFORE_CATCH_PARENTHESES);
    }
    else if (myType1 == mLPAREN || myType2 == mRPAREN) {
      createSpaceInCode(mySettings.SPACE_WITHIN_CATCH_PARENTHESES);
    }
  }

  public void visitFinallyClause(GrFinallyClause catchClause) {
    if (isOpenBlock(myType2)) {
      createSpaceBeforeLBrace(mySettings.SPACE_BEFORE_FINALLY_LBRACE, mySettings.BRACE_STYLE, null,
                              mySettings.KEEP_SIMPLE_BLOCKS_IN_ONE_LINE);
    }
  }

  public void visitTryStatement(GrTryCatchStatement tryCatchStatement) {
    if (myType2 == FINALLY_CLAUSE) {
      processOnNewLineCondition(mySettings.FINALLY_ON_NEW_LINE, mySettings.SPACE_BEFORE_FINALLY_KEYWORD);
    }
    else if (isOpenBlock(myType2)) {
      createSpaceBeforeLBrace(mySettings.SPACE_BEFORE_TRY_LBRACE, mySettings.BRACE_STYLE, null, mySettings.KEEP_SIMPLE_BLOCKS_IN_ONE_LINE);
    }
    else if (myType2 == CATCH_CLAUSE) {
      processOnNewLineCondition(mySettings.CATCH_ON_NEW_LINE, mySettings.SPACE_BEFORE_CATCH_KEYWORD);
    }
  }

  @Override
  public void visitAssignmentExpression(GrAssignmentExpression expression) {
    if (TokenSets.ASSIGN_OP_SET.contains(myType1) || TokenSets.ASSIGN_OP_SET.contains(myType2)) {
      createSpaceInCode(mySettings.SPACE_AROUND_ASSIGNMENT_OPERATORS);
    }
  }

  @Override
  public void visitBinaryExpression(GrBinaryExpression expression) {
    if (isLeftOrRight(LOGICAL_OPERATORS)) {
      createSpaceInCode(mySettings.SPACE_AROUND_LOGICAL_OPERATORS);
    }
    else if (isLeftOrRight(EQUALITY_OPERATORS)) {
      createSpaceInCode(mySettings.SPACE_AROUND_EQUALITY_OPERATORS);
    }
    else if (isLeftOrRight(RELATIONAL_OPERATORS)) {
      createSpaceInCode(mySettings.SPACE_AROUND_RELATIONAL_OPERATORS);
    }
    else if (isLeftOrRight(BITWISE_OPERATORS)) {
      createSpaceInCode(mySettings.SPACE_AROUND_BITWISE_OPERATORS);
    }
    else if (isLeftOrRight(ADDITIVE_OPERATORS)) {
      createSpaceInCode(mySettings.SPACE_AROUND_ADDITIVE_OPERATORS);
    }
    else if (isLeftOrRight(MULTIPLICATIVE_OPERATORS)) {
      createSpaceInCode(mySettings.SPACE_AROUND_MULTIPLICATIVE_OPERATORS);
    }
    else if (isLeftOrRight(SHIFT_OPERATORS)) {
      createSpaceInCode(mySettings.SPACE_AROUND_SHIFT_OPERATORS);
    }
  }

  private boolean isLeftOrRight(TokenSet operators) {
    return operators.contains(myType1) || operators.contains(myType2);
  }

  @Override
  public void visitUnaryExpression(GrUnaryExpression expression) {
    if (!expression.isPostfix() && expression.getOperationToken() == myChild1 ||
        expression.isPostfix() && expression.getOperationToken() == myChild2) {
      createSpaceInCode(mySettings.SPACE_AROUND_UNARY_OPERATOR);
    }
  }

  public void visitSwitchStatement(GrSwitchStatement switchStatement) {
    if (myType1 == kSWITCH && myType2 == mLPAREN) {
      createSpaceInCode(mySettings.SPACE_BEFORE_SWITCH_PARENTHESES);
    } else if (myType1 == mLPAREN || myType2 == mRPAREN) {
      createSpaceInCode(mySettings.SPACE_WITHIN_SWITCH_PARENTHESES);
    } else if (myType2 == mLCURLY) {
      createSpaceBeforeLBrace(mySettings.SPACE_BEFORE_SWITCH_LBRACE, mySettings.BRACE_STYLE, null,
                              mySettings.KEEP_SIMPLE_BLOCKS_IN_ONE_LINE);
    }
  }

  public void visitSynchronizedStatement(GrSynchronizedStatement synchronizedStatement) {
    if (myType1 == kSYNCHRONIZED || myType2 == mLPAREN) {
      createSpaceInCode(mySettings.SPACE_BEFORE_SYNCHRONIZED_PARENTHESES);
    } else if (myType1 == mLPAREN || myType2 == mRPAREN) {
      createSpaceInCode(mySettings.SPACE_WITHIN_SYNCHRONIZED_PARENTHESES);
    } else if (isOpenBlock(myType2)) {
      createSpaceBeforeLBrace(mySettings.SPACE_BEFORE_SYNCHRONIZED_LBRACE, mySettings.BRACE_STYLE, null,
                              mySettings.KEEP_SIMPLE_BLOCKS_IN_ONE_LINE);
    }

  }

  public void visitDocComment(GrDocComment comment) {
    if (myType1 == GDOC_TAG &&
        myType2 == GDOC_TAG &&
        mySettings.getRootSettings().JD_LEADING_ASTERISKS_ARE_ENABLED) {
      IElementType type = myChild1.getLastChildNode().getElementType();
      if (type == mGDOC_ASTERISKS) {
        myResult = Spacing.createSpacing(1, 1, 0, mySettings.KEEP_LINE_BREAKS, mySettings.KEEP_BLANK_LINES_IN_CODE);
      }
    }
  }

  public void visitDocTag(GrDocTag docTag) {
    if (myType1 == mGDOC_INLINE_TAG_START ||
        myType2 == mGDOC_INLINE_TAG_END) {
      myResult = Spacing.createSpacing(0, 0, 0, mySettings.KEEP_LINE_BREAKS, mySettings.KEEP_BLANK_LINES_IN_CODE);
    }
  }

  @Override
  public void visitNamedArgument(GrNamedArgument argument) {
    if (myType1 == mCOLON) {
      if (myGroovySettings.SPACE_IN_NAMED_ARGUMENT) {
        myResult = Spacing.createSpacing(1, 1, 0, mySettings.KEEP_LINE_BREAKS, mySettings.KEEP_BLANK_LINES_IN_CODE);
      }
      else {
        myResult = Spacing.createSpacing(0, 0, 0, mySettings.KEEP_LINE_BREAKS, mySettings.KEEP_BLANK_LINES_IN_CODE);
      }
    }
  }

  @Override
  public void visitListOrMap(GrListOrMap listOrMap) {
    if (myType1 == mLBRACK || myType2 == mRBRACK) {
      createSpaceInCode(myGroovySettings.SPACE_WITHIN_LIST_OR_MAP);
    }
  }

  @Override
  public void visitParenthesizedExpression(GrParenthesizedExpression expression) {
    if (myType1 == mLPAREN || myType2 == mRPAREN) {
      createSpaceInCode(mySettings.SPACE_WITHIN_PARENTHESES);
    }
  }

  @Override
  public void visitAnnotationArrayInitializer(GrAnnotationArrayInitializer arrayInitializer) {
    if (myType1 == mLBRACK || myType2 == mRBRACK) {
      createSpaceInCode(mySettings.SPACE_WITHIN_BRACKETS);
    }
  }



  public void visitIfStatement(GrIfStatement ifStatement) {
    if (myType2 == kELSE) {
      if (!isOpenBlock(myType1) && myType1 != BLOCK_STATEMENT) {
        myResult = Spacing.createSpacing(1, 1, 0, mySettings.KEEP_LINE_BREAKS, mySettings.KEEP_BLANK_LINES_IN_CODE);
      }
      else {
        if (mySettings.ELSE_ON_NEW_LINE) {
          myResult = Spacing.createSpacing(0, 0, 1, mySettings.KEEP_LINE_BREAKS, mySettings.KEEP_BLANK_LINES_IN_CODE);
        }
        else {
          createSpaceProperty(mySettings.SPACE_BEFORE_ELSE_KEYWORD, false, 0);
        }
      }
    }
    else if (myType1 == kELSE) {
      if (myType2 == IF_STATEMENT) {
        if (mySettings.SPECIAL_ELSE_IF_TREATMENT) {
          createSpaceProperty(true, false, 0);
        }
        else {
          myResult = Spacing.createSpacing(1, 1, 1, mySettings.KEEP_LINE_BREAKS, mySettings.KEEP_BLANK_LINES_IN_CODE);
        }
      }
      else {
        if (myType2 == BLOCK_STATEMENT || isOpenBlock(myType2)) {
          createSpaceBeforeLBrace(mySettings.SPACE_BEFORE_ELSE_LBRACE, mySettings.BRACE_STYLE, null,
                                  mySettings.KEEP_SIMPLE_BLOCKS_IN_ONE_LINE);
        }
        else {
          createSpacingBeforeElementInsideControlStatement();
        }
      }
    }
    else if (myType2 == BLOCK_STATEMENT || isOpenBlock(myType2)) {
      boolean space = myChild2.getPsi() == ((GrIfStatement)myParent).getElseBranch()
                      ? mySettings.SPACE_BEFORE_ELSE_LBRACE
                      : mySettings.SPACE_BEFORE_IF_LBRACE;
      createSpaceBeforeLBrace(space, mySettings.BRACE_STYLE,
                              new TextRange(myParent.getTextRange().getStartOffset(), myChild1.getTextRange().getEndOffset()),
                              mySettings.KEEP_SIMPLE_BLOCKS_IN_ONE_LINE);
    }
    else if (myType2 == mLPAREN) {
      createSpaceInCode(mySettings.SPACE_BEFORE_IF_PARENTHESES);
    }
    else if (myType1 == mLPAREN || myType2 == mRPAREN) {
      createSpaceInCode(mySettings.SPACE_WITHIN_IF_PARENTHESES);
    }
    else if (((GrIfStatement)myParent).getThenBranch() == myChild2.getPsi()) {
      createSpacingBeforeElementInsideControlStatement();
    }
  }

  public void visitForStatement(GrForStatement forStatement) {
    if (myType2 == mLPAREN) {
      createSpaceInCode(mySettings.SPACE_BEFORE_FOR_PARENTHESES);
    } else if (myType1 == mLPAREN) {
      ASTNode rparenth = findFrom(myChild2, mRPAREN, true);
      if (rparenth == null) {
        createSpaceInCode(mySettings.SPACE_WITHIN_FOR_PARENTHESES);
      } else {
        createParenthSpace(mySettings.FOR_STATEMENT_LPAREN_ON_NEXT_LINE, mySettings.SPACE_WITHIN_FOR_PARENTHESES,
                           new TextRange(myChild1.getTextRange().getStartOffset(), rparenth.getTextRange().getEndOffset()));
      }
    } else if (myType2 == mRPAREN) {
      ASTNode lparenth = findFrom(myChild2, mLPAREN, false);
      if (lparenth == null) {
        createSpaceInCode(mySettings.SPACE_WITHIN_FOR_PARENTHESES);
      } else {
        createParenthSpace(mySettings.FOR_STATEMENT_RPAREN_ON_NEXT_LINE, mySettings.SPACE_WITHIN_FOR_PARENTHESES,
                           new TextRange(lparenth.getTextRange().getStartOffset(), myChild2.getTextRange().getEndOffset()));
      }

    } else if (myType2 == BLOCK_STATEMENT || isOpenBlock(myType2)) {
      if (myType2 == BLOCK_STATEMENT) {
        createSpaceBeforeLBrace(mySettings.SPACE_BEFORE_FOR_LBRACE, mySettings.BRACE_STYLE,
                                new TextRange(myParent.getTextRange().getStartOffset(), myChild1.getTextRange().getEndOffset()),
                                mySettings.KEEP_SIMPLE_BLOCKS_IN_ONE_LINE);
      } else if (mySettings.KEEP_CONTROL_STATEMENT_IN_ONE_LINE) {
        myResult = Spacing.createDependentLFSpacing(1, 1, myParent.getTextRange(), false, mySettings.KEEP_BLANK_LINES_IN_CODE);
      } else {
        myResult = Spacing.createSpacing(0, 0, 1, false, mySettings.KEEP_BLANK_LINES_IN_CODE);
      }
    }
  }

  private static boolean isOpenBlock(IElementType type) {
    return type == OPEN_BLOCK || type == CONSTRUCTOR_BODY;
  }

  private void createParenthSpace(final boolean onNewLine, final boolean space, final TextRange dependence) {
    if (onNewLine) {
      final int spaces = space ? 1 : 0;
      myResult = Spacing
        .createDependentLFSpacing(spaces, spaces, dependence, mySettings.KEEP_LINE_BREAKS, mySettings.KEEP_BLANK_LINES_IN_CODE);
    } else {
      createSpaceInCode(space);
    }
  }


  @Nullable
  private static ASTNode findFrom(ASTNode current, final IElementType expected, boolean forward) {
    while (current != null) {
      if (current.getElementType() == expected) return current;
      current = forward ? current.getTreeNext() : current.getTreePrev();
    }
    return null;
  }


  private void processOnNewLineCondition(boolean onNewLine, boolean spaceIfNotNewLine) {
    if (onNewLine) {
      if (mySettings.KEEP_SIMPLE_BLOCKS_IN_ONE_LINE) {
        myResult =
          Spacing.createDependentLFSpacing(0, 1, myParent.getTextRange(), mySettings.KEEP_LINE_BREAKS, mySettings.KEEP_BLANK_LINES_IN_CODE);
      }
      else {
        myResult = Spacing.createSpacing(0, 0, 1, mySettings.KEEP_LINE_BREAKS, mySettings.KEEP_BLANK_LINES_IN_CODE);
      }
    }
    else {
      createSpaceProperty(spaceIfNotNewLine, mySettings.KEEP_LINE_BREAKS, mySettings.KEEP_BLANK_LINES_IN_CODE);
    }
  }


  private void createSpacingBeforeElementInsideControlStatement() {
    if (mySettings.KEEP_CONTROL_STATEMENT_IN_ONE_LINE && myType1 != mSL_COMMENT) {
      createSpaceProperty(true, mySettings.KEEP_LINE_BREAKS, mySettings.KEEP_BLANK_LINES_IN_CODE);
    } else {
      myResult = Spacing.createSpacing(1, 1, 1, mySettings.KEEP_LINE_BREAKS, mySettings.KEEP_BLANK_LINES_IN_CODE);
    }
  }


  private void processModifierList(ASTNode modifierList) {
    if (modifierList.getLastChildNode().getElementType() == ANNOTATION && mySettings.METHOD_ANNOTATION_WRAP == CommonCodeStyleSettings.WRAP_ALWAYS ||
        mySettings.MODIFIER_LIST_WRAP) {
      myResult = Spacing.createSpacing(0, 0, 1, mySettings.KEEP_LINE_BREAKS, mySettings.KEEP_BLANK_LINES_IN_CODE);
    }
    else {
      createSpaceProperty(true, false, 0);
    }
  }

  public Spacing getSpacing() {
    return myResult;
  }

  private void createSpaceInCode(final boolean space) {
    createSpaceProperty(space, mySettings.KEEP_BLANK_LINES_IN_CODE);
  }

  private void createSpaceProperty(boolean space, int keepBlankLines) {
    createSpaceProperty(space, mySettings.KEEP_LINE_BREAKS, keepBlankLines);
  }

  private void createSpaceProperty(boolean space, boolean keepLineBreaks, final int keepBlankLines) {
    myResult = Spacing.createSpacing(space ? 1 : 0, space ? 1 : 0, 0, keepLineBreaks, keepBlankLines);
  }

  private void createSpaceBeforeLBrace(final boolean spaceBeforeLbrace,
                                       int braceStyle,
                                       @Nullable TextRange dependantRange,
                                       boolean keepOneLine) {
    if (dependantRange != null && braceStyle == CommonCodeStyleSettings.NEXT_LINE_IF_WRAPPED) {
      int space = spaceBeforeLbrace ? 1 : 0;
      myResult = createNonLFSpace(space, dependantRange, false);
    }
    else if (braceStyle == CommonCodeStyleSettings.END_OF_LINE || braceStyle == CommonCodeStyleSettings.NEXT_LINE_IF_WRAPPED) {
      int space = spaceBeforeLbrace ? 1 : 0;
      myResult = createNonLFSpace(space, null, false);
    }
    else if (keepOneLine) {
      int space = spaceBeforeLbrace ? 1 : 0;
      myResult = Spacing.createDependentLFSpacing(space, space, myParent.getTextRange(), mySettings.KEEP_LINE_BREAKS, mySettings.KEEP_BLANK_LINES_IN_CODE);
    }
    else {
      myResult = Spacing.createSpacing(0, 0, 1, false, mySettings.KEEP_BLANK_LINES_IN_CODE);
    }
  }

  private Spacing createNonLFSpace(int spaces, @Nullable final TextRange dependantRange, final boolean keepLineBreaks) {
    final ASTNode prev = getPrevElementType(myChild2);
    if (prev != null && prev.getElementType() == mSL_COMMENT) {
      return Spacing.createSpacing(0, Integer.MAX_VALUE, 1, keepLineBreaks, mySettings.KEEP_BLANK_LINES_IN_CODE);
    } else if (dependantRange != null) {
      return Spacing.createDependentLFSpacing(spaces, spaces, dependantRange, keepLineBreaks, mySettings.KEEP_BLANK_LINES_IN_CODE);
    } else {
      return Spacing.createSpacing(spaces, spaces, 0, keepLineBreaks, mySettings.KEEP_BLANK_LINES_IN_CODE);
    }
  }

  static boolean isWhiteSpace(final ASTNode node) {
    return node != null && (TokenSets.WHITE_SPACES_SET.contains(node.getElementType()) || node.getTextLength() == 0);
  }

  @Nullable
  static ASTNode getPrevElementType(final ASTNode child) {
    return FormatterUtil.getPreviousNonWhitespaceLeaf(child);
  }
}

