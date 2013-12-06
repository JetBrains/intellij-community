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

import com.intellij.formatting.Spacing;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.formatter.FormatterUtil;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.tree.CompositeElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.codeStyle.GroovyCodeStyleSettings;
import org.jetbrains.plugins.groovy.formatter.FormattingContext;
import org.jetbrains.plugins.groovy.formatter.GeeseUtil;
import org.jetbrains.plugins.groovy.formatter.blocks.GroovyBlock;
import org.jetbrains.plugins.groovy.formatter.blocks.ParameterListBlock;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.*;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase;
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
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrIndexProperty;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrAnonymousClassDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinitionBody;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrAnnotationMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrEnumConstant;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.GrTopStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrArrayTypeElement;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeParameterList;

import static org.jetbrains.plugins.groovy.GroovyFileType.GROOVY_LANGUAGE;
import static org.jetbrains.plugins.groovy.formatter.models.spacing.SpacingTokens.*;
import static org.jetbrains.plugins.groovy.formatter.models.spacing.SpacingTokens.GROOVY_DOC_COMMENT;
import static org.jetbrains.plugins.groovy.formatter.models.spacing.SpacingTokens.kIN;
import static org.jetbrains.plugins.groovy.formatter.models.spacing.SpacingTokens.mCOMMA;
import static org.jetbrains.plugins.groovy.formatter.models.spacing.SpacingTokens.mELVIS;
import static org.jetbrains.plugins.groovy.formatter.models.spacing.SpacingTokens.mGDOC_COMMENT_DATA;
import static org.jetbrains.plugins.groovy.formatter.models.spacing.SpacingTokens.mGDOC_COMMENT_END;
import static org.jetbrains.plugins.groovy.formatter.models.spacing.SpacingTokens.mGDOC_COMMENT_START;
import static org.jetbrains.plugins.groovy.formatter.models.spacing.SpacingTokens.mQUESTION;
import static org.jetbrains.plugins.groovy.formatter.models.spacing.SpacingTokens.mSEMI;
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
  private PsiElement myParent;

  private final GroovyCodeStyleSettings myGroovySettings;
  private final CommonCodeStyleSettings mySettings;

  private Spacing myResult;
  private ASTNode myChild1;
  private ASTNode myChild2;
  private IElementType myType1;
  private IElementType myType2;

  public GroovySpacingProcessor(GroovyBlock block1, GroovyBlock block2, FormattingContext context) {
    mySettings = context.getSettings();
    myGroovySettings = context.getGroovySettings();

    final ASTNode node = block2.getNode();

    if (init(node)) return;
    if (manageComments()) return;
    if (manageMethodParameterList(block2)) return;

    if (myParent instanceof GroovyPsiElement) {
      ((GroovyPsiElement) myParent).accept(this);
    }
  }

  private boolean init(ASTNode node) {
    _init(node);

    if (myChild1 == null || myChild2 == null) {
      return true;
    }

    PsiElement psi1 = myChild1.getPsi();
    PsiElement psi2 = myChild2.getPsi();
    if (psi1 == null || psi2 == null) {
      return true;
    }

    if (psi1.getLanguage() != GROOVY_LANGUAGE || psi2.getLanguage() != GROOVY_LANGUAGE) {
      return true;
    }
    return false;
  }

  private void _init(@Nullable final ASTNode child) {
    if (child == null) return;

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

  private boolean manageMethodParameterList(GroovyBlock block2) {
    if (block2 instanceof ParameterListBlock) {
      createSpaceInCode(mySettings.SPACE_BEFORE_METHOD_PARENTHESES);
      return true;
    }
    return false;
  }

  private boolean manageComments() {
    if (mySettings.KEEP_FIRST_COLUMN_COMMENT && COMMENT_SET.contains(myType2)) {
      if (!isAfterElementOrSemi(IMPORT_STATEMENT)) {
        myResult = Spacing.createKeepingFirstColumnSpacing(0, Integer.MAX_VALUE, true, 1);
        return true;
      }
      return false;
    }

    ASTNode prev = FormatterUtil.getPreviousNonWhitespaceLeaf(myChild2);
    if (prev != null && prev.getElementType() == mNLS) {
      prev = FormatterUtil.getPreviousNonWhitespaceLeaf(prev);
    }
    if (prev != null && prev.getElementType() == mSL_COMMENT) {
      myResult = Spacing.createSpacing(0, Integer.MAX_VALUE, 1, mySettings.KEEP_LINE_BREAKS, keepBlankLines());
      return true;
    }
    return false;
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
  public void visitLabeledStatement(GrLabeledStatement labeledStatement) {
    if (myType1 == mCOLON) {
      if (myGroovySettings.INDENT_LABEL_BLOCKS && !(myType2 == LITERAL)) {
        createLF(true);
      }
      else {
        createSpaceInCode(true);
      }
    }
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
    else {
      processParentheses(mLPAREN,
                         mRPAREN,
                         mySettings.SPACE_WITHIN_METHOD_CALL_PARENTHESES,
                         mySettings.SPACE_WITHIN_EMPTY_METHOD_CALL_PARENTHESES,
                         mySettings.CALL_PARAMETERS_LPAREN_ON_NEXT_LINE,
                         mySettings.CALL_PARAMETERS_RPAREN_ON_NEXT_LINE);
    }
  }

  private void createDependentLFSpacing(final boolean isLineFeed, final boolean isSpace, @NotNull final TextRange range) {
    if (isLineFeed) {
      myResult = Spacing.createDependentLFSpacing(isSpace ? 1 : 0, isSpace ? 1 : 0, range, mySettings.KEEP_LINE_BREAKS, keepBlankLines());
    }
    else {
      createSpaceInCode(isSpace);
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
    if (myType2 == ARGUMENTS) {
      manageSpaceBeforeCallLParenth();
    }
    else if (myType2 == CLOSABLE_BLOCK) {
      createSpaceInCode(myGroovySettings.SPACE_BEFORE_CLOSURE_LBRACE);
    }
  }

  private void manageSpaceBeforeCallLParenth() {
    createSpaceInCode(mySettings.SPACE_BEFORE_METHOD_CALL_PARENTHESES);
  }

  @Override
  public void visitApplicationStatement(GrApplicationStatement applicationStatement) {
    if (myType2 == ARGUMENTS) manageSpaceBeforeCallLParenth();
  }

  @Override
  public void visitIndexProperty(GrIndexProperty expression) {
    if (myType2 == ARGUMENTS) manageSpaceBeforeCallLParenth();
  }

  @Override
  public void visitConstructorInvocation(GrConstructorInvocation invocation) {
    if (myType2 == ARGUMENTS) manageSpaceBeforeCallLParenth();
  }

  public void visitNewExpression(GrNewExpression newExpression) {
    if (myType1 == kNEW) {
      createSpaceInCode(true);
    }
    else if (myType2 == ARGUMENTS) {
      manageSpaceBeforeCallLParenth();
    }
    else if (myType2 == ARRAY_DECLARATOR) {
      createSpaceInCode(false);
    }
  }

  @Override
  public void visitArrayDeclaration(GrArrayDeclaration arrayDeclaration) {
    createSpaceInCode(false);
  }

  @Override
  public void visitArrayTypeElement(GrArrayTypeElement typeElement) {
    createSpaceInCode(false);
  }

  private void manageSpaceInTuple() {
    if (myType1 == mLPAREN || myType2 == mRPAREN) {
      createSpaceInCode(myGroovySettings.SPACE_WITHIN_TUPLE_EXPRESSION);
    }
  }

  @Override
  public void visitEnumConstant(GrEnumConstant enumConstant) {
    manageSpaceBeforeCallLParenth();
  }

  @Override
  public void visitVariableDeclaration(GrVariableDeclaration variableDeclaration) {
    manageSpaceInTuple();
  }


  @Override
  public void visitTupleExpression(GrTupleExpression tupleExpression) {
    manageSpaceInTuple();
  }

  @Override
  public void visitFile(GroovyFileBase file) {
    if (isAfterElementOrSemi(PACKAGE_DEFINITION)) {
      myResult = Spacing.createSpacing(0, 0, mySettings.BLANK_LINES_AFTER_PACKAGE + 1, mySettings.KEEP_LINE_BREAKS, Integer.MAX_VALUE / 2);
    }
    else if (myType2 == PACKAGE_DEFINITION) {
      myResult = Spacing.createSpacing(0, 0, mySettings.BLANK_LINES_BEFORE_PACKAGE + 1, mySettings.KEEP_LINE_BREAKS, Integer.MAX_VALUE / 2);
    }
    else if (isLeftOrRight(TYPE_DEFINITION_TYPES)) {
      if (myType1 == GROOVY_DOC_COMMENT) {
        createLF(true);
      }
      else {
        myResult = Spacing.createSpacing(0, 0, mySettings.BLANK_LINES_AROUND_CLASS + 1, mySettings.KEEP_LINE_BREAKS, mySettings.KEEP_BLANK_LINES_IN_DECLARATIONS);
      }
    }
    else if (isAfterElementOrSemi(IMPORT_STATEMENT) && myType2 != IMPORT_STATEMENT) { //after imports
      myResult = Spacing.createSpacing(0, 0, mySettings.BLANK_LINES_AFTER_IMPORTS + 1, mySettings.KEEP_LINE_BREAKS, Integer.MAX_VALUE / 2);
    }
    else if (myType1 != IMPORT_STATEMENT && !isSemiAfter(IMPORT_STATEMENT) && myType2 == IMPORT_STATEMENT) { //before imports
      myResult = Spacing.createSpacing(0, 0, mySettings.BLANK_LINES_BEFORE_IMPORTS, mySettings.KEEP_LINE_BREAKS, Integer.MAX_VALUE / 2);
    }
    else if (isAfterElementOrSemi(IMPORT_STATEMENT) && myType2 == IMPORT_STATEMENT) {
      myResult = Spacing.createSpacing(0, 0, 1, mySettings.KEEP_LINE_BREAKS, Integer.MAX_VALUE / 2);
    }
    else {
      processClassMembers(null);
    }
  }

  private boolean isAfterElementOrSemi(final IElementType elementType) {
    return myType1 == elementType && myType2 != mSEMI || isSemiAfter(elementType);
  }

  private boolean isSemiAfter(@NotNull IElementType statement) {
    return myType1 == mSEMI && getStatementTypeBySemi(myChild1) == statement;
  }

  private boolean isSemiAfter(@NotNull TokenSet set) {
    return myType1 == mSEMI && set.contains(getStatementTypeBySemi(myChild1));
  }

  @Nullable
  private static IElementType getStatementTypeBySemi(@NotNull ASTNode semi) {
    final GrTopStatement statement = getStatementBySemicolon(semi.getPsi());
    if (statement == null) return null;
    return statement.getNode().getElementType();
  }

  @Nullable
  private static GrTopStatement getStatementBySemicolon(@NotNull PsiElement semi) {
    PsiElement prev = semi.getPrevSibling();
    while (prev != null &&
           TokenSets.WHITE_SPACES_OR_COMMENTS.contains(prev.getNode().getElementType()) &&
           prev.getText().indexOf('\n') < 0) {
      prev = prev.getPrevSibling();
    }

    if (prev instanceof GrTopStatement) return (GrTopStatement)prev;
    return null;
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
      createSpaceInCode(false);
    }
    else if (myType1 == mLCURLY && myType2 != PARAMETERS_LIST && myType2 != mCLOSABLE_BLOCK_OP || myType2 == mRCURLY) { //spaces between statements
      boolean spacesWithinBraces = closure.getParent() instanceof GrStringInjection
                  ? myGroovySettings.SPACE_WITHIN_GSTRING_INJECTION_BRACES
                  : mySettings.SPACE_WITHIN_BRACES;
      createDependentLFSpacing(true, spacesWithinBraces, closure.getTextRange());
    }
    else if (myType1 == mCLOSABLE_BLOCK_OP) {
      myResult = GroovySpacingProcessorBasic.createDependentSpacingForClosure(mySettings, myGroovySettings, closure, true);
    }
    else if (myType1 == mLCURLY && (myType2 == PARAMETERS_LIST || myType2 == mCLOSABLE_BLOCK_OP)) {
      boolean spacesWithinBraces = closure.getParent() instanceof GrStringInjection
                                   ? myGroovySettings.SPACE_WITHIN_GSTRING_INJECTION_BRACES
                                   : mySettings.SPACE_WITHIN_BRACES;
      createSpaceInCode(spacesWithinBraces);
    }

  }

  public void visitOpenBlock(GrOpenBlock block) {
    boolean isMethod = block.getParent() instanceof GrMethod;
    boolean keepInOneLine = isMethod ? mySettings.KEEP_SIMPLE_METHODS_IN_ONE_LINE : mySettings.KEEP_SIMPLE_BLOCKS_IN_ONE_LINE;

    if (myType1 == mLCURLY && myType2 == mRCURLY) {
      createLF(!keepInOneLine);
    }
    else if (myType1 == mLCURLY) {
      if (keepInOneLine) {
        createDependentLFSpacing(true, mySettings.SPACE_WITHIN_BRACES, block.getTextRange());
      }
      else {
        int lineFeedsCount = isMethod ? mySettings.BLANK_LINES_BEFORE_METHOD_BODY + 1 : 1;
        myResult = Spacing.createSpacing(0, 0, lineFeedsCount, mySettings.KEEP_LINE_BREAKS, keepBlankLines());
      }
    }
    else if (myType2 == mRCURLY) {
      if (keepInOneLine) {
        createDependentLFSpacing(true, mySettings.SPACE_WITHIN_BRACES, block.getTextRange());
      }
      else {
        createLF(true);
      }
    }
  }

  public void visitTypeDefinition(GrTypeDefinition typeDefinition) {
    if (myType2 == CLASS_BODY) {
      if (typeDefinition instanceof GrAnonymousClassDefinition) {
        createSpaceProperty(mySettings.SPACE_BEFORE_CLASS_LBRACE, true, 100); //don't manually remove line feeds because this line is ambiguous
      }
      else {
        PsiElement nameIdentifier = typeDefinition.getNameIdentifierGroovy();
        int dependenceStart = nameIdentifier.getTextRange().getStartOffset();
        final TextRange range = new TextRange(dependenceStart, myChild1.getTextRange().getEndOffset());
        createSpaceBeforeLBrace(mySettings.SPACE_BEFORE_CLASS_LBRACE, mySettings.CLASS_BRACE_STYLE, range, false);
      }
    }
    else if (myType2 == TYPE_PARAMETER_LIST) {
      createSpaceInCode(false);
    }
    else if (myType2 == ARGUMENTS) {
      manageSpaceBeforeCallLParenth();
    }
  }

  public void visitTypeDefinitionBody(GrTypeDefinitionBody typeDefinitionBody) {
    if (myType1 == mLCURLY && myType2 == mRCURLY) {
      if (mySettings.KEEP_SIMPLE_CLASSES_IN_ONE_LINE) {
        createSpaceInCode(false);
      }
      else {
        createLF(true);
      }
    }
    else if (myType1 == mLCURLY) {
      myResult = Spacing.createSpacing(0, 0, mySettings.BLANK_LINES_AFTER_CLASS_HEADER + 1, mySettings.KEEP_LINE_BREAKS, keepBlankLines());
    }
    else if (myType2 == mRCURLY) {
      createLF(true);
    }
    else {
      processClassMembers(typeDefinitionBody);
    }
  }

  private void processClassMembers(@Nullable GrTypeDefinitionBody typeDefinitionBody) {
    final boolean isInterface = typeDefinitionBody != null && ((GrTypeDefinition)typeDefinitionBody.getParent()).isInterface();

    if (myType2 == mSEMI) return;

    if (typeDefinitionBody != null) { //check variable definitions only inside class body
      if ((myType1 == VARIABLE_DEFINITION || isSemiAfter(VARIABLE_DEFINITION)) && TokenSets.METHOD_DEFS.contains(myType2)) {
        final int minBlankLines = Math.max(
          isInterface ? mySettings.BLANK_LINES_AROUND_METHOD_IN_INTERFACE : mySettings.BLANK_LINES_AROUND_METHOD,
          isInterface ? mySettings.BLANK_LINES_AROUND_FIELD_IN_INTERFACE : mySettings.BLANK_LINES_AROUND_FIELD
        );
        myResult = Spacing.createSpacing(0, 0, minBlankLines + 1, mySettings.KEEP_LINE_BREAKS, keepBlankLines());
        return;
      }
      else if (myType1 == VARIABLE_DEFINITION || isSemiAfter(VARIABLE_DEFINITION) || myType2 == VARIABLE_DEFINITION) {
        final int minBlankLines =
          isInterface ? mySettings.BLANK_LINES_AROUND_FIELD_IN_INTERFACE : mySettings.BLANK_LINES_AROUND_FIELD;
        myResult = Spacing.createSpacing(0, 0, minBlankLines + 1, mySettings.KEEP_LINE_BREAKS, keepBlankLines());
        return;
      }
    }

    if (TokenSets.METHOD_DEFS.contains(myType1) || isSemiAfter(TokenSets.METHOD_DEFS) || TokenSets.METHOD_DEFS.contains((myType2))) {
      if (myType1 == GROOVY_DOC_COMMENT) {
        createLF(true);
        return;
      }
      else {
        final int minBlankLines = isInterface ? mySettings.BLANK_LINES_AROUND_METHOD_IN_INTERFACE : mySettings.BLANK_LINES_AROUND_METHOD;
        myResult = Spacing.createSpacing(0, 0, minBlankLines + 1, mySettings.KEEP_LINE_BREAKS, keepBlankLines());
        return;
      }
    }

    if (TokenSets.TYPE_DEFINITIONS.contains(myType1) || isSemiAfter(TokenSets.TYPE_DEFINITIONS) || TokenSets.TYPE_DEFINITIONS.contains((myType2)) ) {
      if (myType1 == GROOVY_DOC_COMMENT) {
        createLF(true);
        return;
      }
      else {
        final int minBlankLines = mySettings.BLANK_LINES_AROUND_CLASS;
        myResult = Spacing.createSpacing(0, 0, minBlankLines + 1, mySettings.KEEP_LINE_BREAKS, keepBlankLines());
        return;
      }

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
    else if (myType1 == mLT && myType2 == TYPE_PARAMETER ||
             myType1 == TYPE_PARAMETER && myType2 == mGT) {
      createSpaceInCode(false);
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
    if (myType1 == mRPAREN && myType2 == THROW_CLAUSE) {
      if (mySettings.THROWS_KEYWORD_WRAP == CommonCodeStyleSettings.WRAP_ALWAYS) {
        createLF(true);
      }
      else {
        createSpaceInCode(true);
      }
    }
    else if (isOpenBlock(myType2)) {
      PsiElement methodName = method.getNameIdentifier();
      int dependencyStart = methodName == null ? myParent.getTextRange().getStartOffset() : methodName.getTextRange().getStartOffset();
      createSpaceBeforeLBrace(mySettings.SPACE_BEFORE_METHOD_LBRACE, mySettings.METHOD_BRACE_STYLE,
                              new TextRange(dependencyStart, myChild1.getTextRange().getEndOffset()),
                              mySettings.KEEP_SIMPLE_METHODS_IN_ONE_LINE);
    }
    else if (myType2 == TYPE_PARAMETER_LIST) {
      createSpaceInCode(true);
    }
    else {
      processParentheses(mLPAREN,
                         mRPAREN,
                         mySettings.SPACE_WITHIN_METHOD_PARENTHESES,
                         mySettings.SPACE_WITHIN_EMPTY_METHOD_PARENTHESES,
                         mySettings.METHOD_PARAMETERS_LPAREN_ON_NEXT_LINE,
                         mySettings.METHOD_PARAMETERS_RPAREN_ON_NEXT_LINE);
    }
  }

  private boolean processParentheses(@NotNull IElementType left,
                                     @NotNull IElementType right,
                                     @NotNull Boolean spaceWithin,
                                     @Nullable Boolean spaceWithinEmpty,
                                     @Nullable Boolean leftLF,
                                     @Nullable Boolean rightLF) {
    if (myType1 == left && myType2 == right && spaceWithinEmpty != null) {
      createSpaceInCode(spaceWithinEmpty);
      return true;
    }
    else if (myType1 == left) {
      final ASTNode rparenth = findFrom(myChild1, right, true);
      if (rparenth == null || leftLF == null) {
        createSpaceInCode(spaceWithin);
      }
      else {
        final TextRange range = new TextRange(myChild1.getStartOffset(), rparenth.getTextRange().getEndOffset());
        createDependentLFSpacing(leftLF, spaceWithin, range);
      }
      return true;
    }
    else if (myType2 == right) {
      final ASTNode lparenth = findFrom(myChild1, left, false);
      if (lparenth == null || rightLF == null) {
        createSpaceInCode(spaceWithin);
      }
      else {
        final TextRange range = new TextRange(lparenth.getStartOffset(), myChild2.getTextRange().getEndOffset());
        createDependentLFSpacing(rightLF, spaceWithin, range);
      }
      return true;
    }
    else {
      return false;
    }
  }


  @Override
  public void visitAnnotationMethod(GrAnnotationMethod annotationMethod) {
    if (myType2 == DEFAULT_ANNOTATION_VALUE) {
      createSpaceInCode(true);
    }
    else {
      super.visitAnnotationMethod(annotationMethod);
    }
  }

  public void visitDocMethodReference(GrDocMethodReference reference) {
    visitDocMember();
  }

  public void visitDocFieldReference(GrDocFieldReference reference) {
    visitDocMember();
  }

  private void visitDocMember() {
    createSpaceProperty(false, false, 0);
  }

  public void visitDocMethodParameterList(GrDocMethodParams params) {
    if (myType1 == mGDOC_TAG_VALUE_LPAREN || myType2 == mGDOC_TAG_VALUE_RPAREN || myType2 == mGDOC_TAG_VALUE_COMMA) {
      createSpaceProperty(false, false, 0);
    }
    else {
      createSpaceInCode(true);
    }
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
      processTryOnNewLineCondition(mySettings.FINALLY_ON_NEW_LINE, mySettings.SPACE_BEFORE_FINALLY_KEYWORD);
    }
    else if (isOpenBlock(myType2)) {
      createSpaceBeforeLBrace(mySettings.SPACE_BEFORE_TRY_LBRACE, mySettings.BRACE_STYLE, null, mySettings.KEEP_SIMPLE_BLOCKS_IN_ONE_LINE);
    }
    else if (myType2 == CATCH_CLAUSE) {
      processTryOnNewLineCondition(mySettings.CATCH_ON_NEW_LINE, mySettings.SPACE_BEFORE_CATCH_KEYWORD);
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

    @SuppressWarnings("SimplifiableConditionalExpression" )
    boolean spaceAround = isLeftOrRight(LOGICAL_OPERATORS)        ? mySettings.SPACE_AROUND_LOGICAL_OPERATORS :
                          isLeftOrRight(EQUALITY_OPERATORS)       ? mySettings.SPACE_AROUND_EQUALITY_OPERATORS :
                          isLeftOrRight(RELATIONAL_OPERATORS)     ? mySettings.SPACE_AROUND_RELATIONAL_OPERATORS :
                          isLeftOrRight(BITWISE_OPERATORS)        ? mySettings.SPACE_AROUND_BITWISE_OPERATORS :
                          isLeftOrRight(ADDITIVE_OPERATORS)       ? mySettings.SPACE_AROUND_ADDITIVE_OPERATORS :
                          isLeftOrRight(MULTIPLICATIVE_OPERATORS) ? mySettings.SPACE_AROUND_MULTIPLICATIVE_OPERATORS :
                          isLeftOrRight(SHIFT_OPERATORS)          ? mySettings.SPACE_AROUND_SHIFT_OPERATORS :
                          isLeftOrRight(REGEX_OPERATORS)          ? myGroovySettings.SPACE_AROUND_REGEX_OPERATORS :
                          isLeftOrRight(kIN);
    if (TokenSets.BINARY_OP_SET.contains(myType2)) {
      createDependentLFSpacing(mySettings.BINARY_OPERATION_SIGN_ON_NEXT_LINE, spaceAround, expression.getTextRange());
    }
    else {
      createSpaceInCode(spaceAround);
    }
  }

  private boolean isLeftOrRight(TokenSet operators) {
    return operators.contains(myType1) || operators.contains(myType2);
  }

  private boolean isLeftOrRight(IElementType type) {
    return myType1 == type || myType2 == type;
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
    }
    else if (myType1 == mLPAREN || myType2 == mRPAREN) {
      createSpaceInCode(mySettings.SPACE_WITHIN_SWITCH_PARENTHESES);
    }
    else if (myType2 == mLCURLY) {
      createSpaceBeforeLBrace(mySettings.SPACE_BEFORE_SWITCH_LBRACE, mySettings.BRACE_STYLE, null,
                              mySettings.KEEP_SIMPLE_BLOCKS_IN_ONE_LINE);
    }
    else if (myType1 == mLCURLY || myType2 == mRCURLY) {
      createLF(true);
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
        createSpaceInCode(true);
      }
    }

    if (myType1 == mGDOC_COMMENT_START && myType2 == mGDOC_COMMENT_DATA ||
        myType1 == mGDOC_COMMENT_DATA &&  myType2 == mGDOC_COMMENT_END ||
        myType1 == mGDOC_ASTERISKS &&     myType2 == mGDOC_COMMENT_END) {
      createLazySpace();
    }
  }

  private void createLazySpace() {
    myResult = Spacing.createSpacing(0, Integer.MAX_VALUE, 0, mySettings.KEEP_LINE_BREAKS, mySettings.KEEP_BLANK_LINES_IN_CODE);
  }

  public void visitDocTag(GrDocTag docTag) {
    if (myType1 == mGDOC_INLINE_TAG_START || myType2 == mGDOC_INLINE_TAG_END) {
      createSpaceInCode(false);
    }
  }

  @Override
  public void visitNamedArgument(GrNamedArgument argument) {
    if (myType1 == mCOLON) {
      createSpaceInCode(myGroovySettings.SPACE_IN_NAMED_ARGUMENT);
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
    processParentheses(mLPAREN, mRPAREN, mySettings.SPACE_WITHIN_PARENTHESES, null, mySettings.PARENTHESES_EXPRESSION_LPAREN_WRAP,
                       mySettings.PARENTHESES_EXPRESSION_RPAREN_WRAP);
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
        createSpaceInCode(true);
      }
      else {
        if (mySettings.ELSE_ON_NEW_LINE) {
          createLF(true);
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
          createLF(true);
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
    }
    else if (myType2 == BLOCK_STATEMENT || isOpenBlock(myType2)) {
      if (myType2 == BLOCK_STATEMENT) {
        createSpaceBeforeLBrace(mySettings.SPACE_BEFORE_FOR_LBRACE, mySettings.BRACE_STYLE,
                                new TextRange(myParent.getTextRange().getStartOffset(), myChild1.getTextRange().getEndOffset()),
                                mySettings.KEEP_SIMPLE_BLOCKS_IN_ONE_LINE);
      }
      else if (mySettings.KEEP_CONTROL_STATEMENT_IN_ONE_LINE) {
        createDependentLFSpacing(true, true, myParent.getTextRange());
      }
      else {
        createLF(true);
      }
    }
    else if (myType1 == mRPAREN) {
      createSpacingBeforeElementInsideControlStatement();
    }
    else {
      processParentheses(mLPAREN,
                         mRPAREN,
                         mySettings.SPACE_WITHIN_FOR_PARENTHESES,
                         null,
                         mySettings.FOR_STATEMENT_LPAREN_ON_NEXT_LINE,
                         mySettings.FOR_STATEMENT_RPAREN_ON_NEXT_LINE);
    }
  }

  private static boolean isOpenBlock(IElementType type) {
    return type == OPEN_BLOCK || type == CONSTRUCTOR_BODY;
  }

  @Nullable
  private static ASTNode findFrom(ASTNode current, final IElementType expected, boolean forward) {
    while (current != null) {
      if (current.getElementType() == expected) return current;
      current = forward ? current.getTreeNext() : current.getTreePrev();
    }
    return null;
  }


  private void processTryOnNewLineCondition(boolean onNewLine, boolean spaceIfNotNewLine) {
    if (onNewLine) {
      if (mySettings.KEEP_SIMPLE_BLOCKS_IN_ONE_LINE) {
        myResult = Spacing.createDependentLFSpacing(0, 1, myParent.getTextRange(), mySettings.KEEP_LINE_BREAKS, keepBlankLines());
      }
      else {
        createLF(true);
      }
    }
    else {
      createSpaceInCode(spaceIfNotNewLine);
    }
  }


  private void createSpacingBeforeElementInsideControlStatement() {
    if (mySettings.KEEP_CONTROL_STATEMENT_IN_ONE_LINE && myType1 != mSL_COMMENT) {
      createSpaceInCode(true);
    }
    else {
      createLF(true);
    }
  }

  public Spacing getSpacing() {
    return myResult;
  }

  private void createSpaceInCode(final boolean space) {
    createSpaceProperty(space, keepBlankLines());
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
      myResult = Spacing.createDependentLFSpacing(space, space, myParent.getTextRange(), mySettings.KEEP_LINE_BREAKS,
                                                  keepBlankLines());
    }
    else {
      createLF(true);
    }
  }

  private void createLF(final boolean lf) {
    myResult = Spacing.createSpacing(0, 0, lf ? 1 : 0, mySettings.KEEP_LINE_BREAKS, keepBlankLines());
  }

  private Spacing createNonLFSpace(int spaces, @Nullable final TextRange dependantRange, final boolean keepLineBreaks) {
    final ASTNode prev = FormatterUtil.getPreviousNonWhitespaceLeaf(myChild2);
    if (prev != null && prev.getElementType() == mSL_COMMENT) {
      return Spacing.createSpacing(0, Integer.MAX_VALUE, 1, keepLineBreaks, keepBlankLines());
    }
    else if (dependantRange != null) {
      return Spacing.createDependentLFSpacing(spaces, spaces, dependantRange, keepLineBreaks, keepBlankLines());
    }
    else {
      return Spacing.createSpacing(spaces, spaces, 0, keepLineBreaks, keepBlankLines());
    }
  }

  private int keepBlankLines() {
    if (myType2 == mRCURLY) {
      return mySettings.KEEP_BLANK_LINES_BEFORE_RBRACE;
    }
    else if (myParent instanceof GrTypeDefinitionBody) {
      return mySettings.KEEP_BLANK_LINES_IN_DECLARATIONS;
    }
    else {
      return mySettings.KEEP_BLANK_LINES_IN_CODE;
    }
  }

  static boolean isWhiteSpace(final ASTNode node) {
    return node != null && (TokenSets.WHITE_SPACES_SET.contains(node.getElementType()) || node.getTextLength() == 0);
  }
}

