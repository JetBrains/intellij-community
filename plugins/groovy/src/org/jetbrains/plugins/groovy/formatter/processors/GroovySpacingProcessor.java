// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.formatter.processors;

import com.intellij.formatting.Block;
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
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyLanguage;
import org.jetbrains.plugins.groovy.codeStyle.GroovyCodeStyleSettings;
import org.jetbrains.plugins.groovy.ext.spock.SpecificationKt;
import org.jetbrains.plugins.groovy.formatter.FormattingContext;
import org.jetbrains.plugins.groovy.formatter.GeeseUtil;
import org.jetbrains.plugins.groovy.formatter.blocks.GroovyBlock;
import org.jetbrains.plugins.groovy.formatter.blocks.GroovyBlockGenerator;
import org.jetbrains.plugins.groovy.formatter.blocks.SyntheticGroovyBlock;
import org.jetbrains.plugins.groovy.formatter.models.spacing.SpacingTokens;
import org.jetbrains.plugins.groovy.lang.groovydoc.lexer.GroovyDocTokenTypes;
import org.jetbrains.plugins.groovy.lang.groovydoc.parser.GroovyDocElementTypes;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.*;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;
import org.jetbrains.plugins.groovy.lang.parser.GrBlockElementType;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.parser.GroovyEmptyStubElementTypes;
import org.jetbrains.plugins.groovy.lang.parser.GroovyStubElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.*;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotation;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotationArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotationArrayInitializer;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrSpreadArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.GrAssertStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.clauses.GrForInClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrStringInjection;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrIndexProperty;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameterList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrAnonymousClassDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrRecordDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinitionBody;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrAnnotationMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrEnumConstant;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.GrTopStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrArrayTypeElement;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeParameterList;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;

import java.util.Iterator;

import static org.jetbrains.plugins.groovy.formatter.blocks.BlocksKt.shouldHandleAsSimpleClosure;
import static org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.*;
import static org.jetbrains.plugins.groovy.lang.psi.GroovyTokenSets.*;

public class GroovySpacingProcessor extends GroovyElementVisitor {
  private PsiElement myParent;

  private final GroovyCodeStyleSettings myGroovySettings;
  private final CommonCodeStyleSettings mySettings;
  private final boolean myForbidNewLineInSpacing;

  private Spacing myResult;
  private ASTNode myChild1;
  private ASTNode myChild2;
  private IElementType myType1;
  private IElementType myType2;

  public GroovySpacingProcessor(GroovyBlock block1, GroovyBlock block2, FormattingContext context) {
    mySettings = context.getSettings();
    myGroovySettings = context.getGroovySettings();
    myForbidNewLineInSpacing = context.isForbidNewLineInSpacing();

    final ASTNode node = block2.getNode();

    if (init(node)) return;
    if (manageComments()) return;

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

    if (psi1.getLanguage() != GroovyLanguage.INSTANCE || psi2.getLanguage() != GroovyLanguage.INSTANCE) {
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

  private boolean manageComments() {
    if (mySettings.KEEP_FIRST_COLUMN_COMMENT && TokenSets.COMMENT_SET.contains(myType2)) {
      if (!isAfterElementOrSemi(GroovyStubElementTypes.IMPORT)) {
        myResult = Spacing.createKeepingFirstColumnSpacing(0, Integer.MAX_VALUE, mySettings.KEEP_LINE_BREAKS, keepBlankLines());
        return true;
      }
      return false;
    }

    ASTNode prev = FormatterUtil.getPreviousNonWhitespaceLeaf(myChild2);
    if (prev != null && prev.getElementType() == GroovyTokenTypes.mNLS) {
      prev = FormatterUtil.getPreviousNonWhitespaceLeaf(prev);
    }
    if (prev != null && prev.getElementType() == GroovyTokenTypes.mSL_COMMENT) {
      myResult = Spacing.createSpacing(0, Integer.MAX_VALUE, 1, mySettings.KEEP_LINE_BREAKS, keepBlankLines());
      return true;
    }
    return false;
  }

  @Override
  public void visitLiteralExpression(@NotNull GrLiteral literal) {
    createSpaceInCode(false);
  }

  @Override
  public void visitGStringInjection(@NotNull GrStringInjection injection) {
    createSpaceInCode(false);
  }

  @Override
  public void visitLabeledStatement(@NotNull GrLabeledStatement labeledStatement) {
    if (myType1 == GroovyTokenTypes.mCOLON) {
      if (myGroovySettings.INDENT_LABEL_BLOCKS && !(myType2 == GroovyElementTypes.LITERAL)) {
        createLF(true);
      }
      else {
        createSpaceInCode(true);
      }
    }
  }

  @Override
  public void visitAnnotation(@NotNull GrAnnotation annotation) {
    if (myType2 == GroovyEmptyStubElementTypes.ANNOTATION_ARGUMENT_LIST) {
      createSpaceInCode(mySettings.SPACE_BEFORE_ANOTATION_PARAMETER_LIST);
    }
  }

  @Override
  public void visitAnnotationArgumentList(@NotNull GrAnnotationArgumentList annotationArgumentList) {
    if (isWithinParentheses()) {
      createSpaceInCode(mySettings.SPACE_WITHIN_ANNOTATION_PARENTHESES);
    }
  }

  @Override
  public void visitArgumentList(@NotNull GrArgumentList list) {
    if (list.getParent() instanceof GrIndexProperty) {
      processBrackets(
        mySettings.SPACE_WITHIN_BRACKETS,
        null,
        mySettings.CALL_PARAMETERS_LPAREN_ON_NEXT_LINE,
        mySettings.CALL_PARAMETERS_RPAREN_ON_NEXT_LINE
      );
    }
    else {
      processParentheses(
        mySettings.SPACE_WITHIN_METHOD_CALL_PARENTHESES,
        mySettings.SPACE_WITHIN_EMPTY_METHOD_CALL_PARENTHESES,
        mySettings.CALL_PARAMETERS_LPAREN_ON_NEXT_LINE,
        mySettings.CALL_PARAMETERS_RPAREN_ON_NEXT_LINE
      );
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
  public void visitLambdaExpression(@NotNull GrLambdaExpression expression) {
    boolean spaceAroundArrow = mySettings.SPACE_AROUND_LAMBDA_ARROW;

    if (myType2 == T_ARROW) {
      createSpaceProperty(spaceAroundArrow, mySettings.KEEP_LINE_BREAKS, keepBlankLines());
    }
    if (myType2 == BLOCK_LAMBDA_BODY) {
      createSpaceBeforeLBrace(spaceAroundArrow, mySettings.LAMBDA_BRACE_STYLE, null, mySettings.KEEP_SIMPLE_LAMBDAS_IN_ONE_LINE);
    }
    if (myType2 == EXPRESSION_LAMBDA_BODY) {
      createSpaceProperty(spaceAroundArrow, mySettings.KEEP_LINE_BREAKS, keepBlankLines());
    }
  }

  @Override
  public void visitBlockLambdaBody(@NotNull GrBlockLambdaBody body) {
    if (myType2 == GroovyTokenTypes.mLCURLY) {
      myResult = Spacing.createSpacing(0, 0, 0, false, 0);
    }
    if (myType1 == GroovyTokenTypes.mLCURLY || myType2 == GroovyTokenTypes.mRCURLY) {
      boolean simpleLambda = shouldHandleAsSimpleLambda(body);
      int spaces = simpleLambda && mySettings.SPACE_WITHIN_BRACES ? 1 : 0;
      myResult = Spacing.createSpacing(spaces, spaces, simpleLambda ? 0 : 1, mySettings.KEEP_LINE_BREAKS, keepBlankLines());
    }
  }

  private boolean shouldHandleAsSimpleLambda(GrBlockLambdaBody lambda) {
    return mySettings.KEEP_SIMPLE_LAMBDAS_IN_ONE_LINE && !lambda.textContains('\n');
  }

  @Override
  public void visitConditionalExpression(@NotNull GrConditionalExpression expression) {
    if (myType2 == GroovyTokenTypes.mQUESTION) {
      createSpaceInCode(mySettings.SPACE_BEFORE_QUEST);
    }
    else if (myType1 == GroovyTokenTypes.mQUESTION) {
      createSpaceInCode(mySettings.SPACE_AFTER_QUEST);
    }
    else if (myType2 == GroovyTokenTypes.mCOLON) {
      createSpaceInCode(mySettings.SPACE_BEFORE_COLON);
    }
    else if (myType1 == GroovyTokenTypes.mCOLON) {
      createSpaceInCode(mySettings.SPACE_AFTER_COLON);
    }
  }

  @Override
  public void visitElvisExpression(@NotNull GrElvisExpression expression) {
    if (myType1 == GroovyTokenTypes.mELVIS) {
      createSpaceInCode(mySettings.SPACE_AFTER_COLON);
    }
    else if (myType2 == GroovyTokenTypes.mELVIS) {
      createSpaceInCode(mySettings.SPACE_BEFORE_QUEST);
    }
  }

  @Override
  public void visitMethodCallExpression(@NotNull GrMethodCallExpression methodCallExpression) {
    if (myType2 == GroovyElementTypes.ARGUMENTS) {
      manageSpaceBeforeCallLParenth();
    }
    else if (myType2 == GroovyElementTypes.CLOSABLE_BLOCK) {
      createSpaceInCode(myGroovySettings.SPACE_BEFORE_CLOSURE_LBRACE);
    }
  }

  private void manageSpaceBeforeCallLParenth() {
    createSpaceInCode(mySettings.SPACE_BEFORE_METHOD_CALL_PARENTHESES);
  }

  @Override
  public void visitApplicationStatement(@NotNull GrApplicationStatement applicationStatement) {
    if (myType2 == GroovyElementTypes.ARGUMENTS) manageSpaceBeforeCallLParenth();
  }

  @Override
  public void visitIndexProperty(@NotNull GrIndexProperty expression) {
    if (myType2 == GroovyTokenTypes.mQUESTION) createSpaceInCode(false);
    if (myType2 == GroovyElementTypes.ARGUMENTS) manageSpaceBeforeCallLParenth();
  }

  @Override
  public void visitConstructorInvocation(@NotNull GrConstructorInvocation invocation) {
    if (myType2 == GroovyElementTypes.ARGUMENTS) manageSpaceBeforeCallLParenth();
  }

  @Override
  public void visitNewExpression(@NotNull GrNewExpression newExpression) {
    if (myType1 == GroovyTokenTypes.kNEW) {
      createSpaceInCode(true);
    }
    else if (myType2 == GroovyElementTypes.ARGUMENTS) {
      manageSpaceBeforeCallLParenth();
    }
    else if (myType2 == GroovyElementTypes.ARRAY_DECLARATOR) {
      createSpaceInCode(false);
    }
    else if (myType2 == ARRAY_INITIALIZER) {
      createSpaceInCode(mySettings.SPACE_BEFORE_ARRAY_INITIALIZER_LBRACE);
    }
  }

  @Override
  public void visitArrayDeclaration(@NotNull GrArrayDeclaration arrayDeclaration) {
    createSpaceInCode(false);
  }

  @Override
  public void visitArrayTypeElement(@NotNull GrArrayTypeElement typeElement) {
    createSpaceInCode(false);
  }

  private void manageSpaceInTuple() {
    if (isWithinParentheses()) {
      createSpaceInCode(myGroovySettings.SPACE_WITHIN_TUPLE_EXPRESSION);
    }
  }

  @Override
  public void visitEnumConstant(@NotNull GrEnumConstant enumConstant) {
    if (myType1 == GroovyStubElementTypes.MODIFIER_LIST) {
      createSpaceInCode(true);
    } else  {
      manageSpaceBeforeCallLParenth();
    }
  }

  @Override
  public void visitVariableDeclaration(@NotNull GrVariableDeclaration variableDeclaration) {
    manageSpaceInTuple();
  }


  @Override
  public void visitTuple(@NotNull GrTuple tuple) {
    manageSpaceInTuple();
  }

  @Override
  public void visitSpreadArgument(@NotNull GrSpreadArgument spreadArgument) {
    if (myType1 == GroovyTokenTypes.mSTAR) {
      createSpaceInCode(mySettings.SPACE_AROUND_UNARY_OPERATOR);
    }
  }

  @Override
  public void visitFile(@NotNull GroovyFileBase file) {
    if (isAfterElementOrSemi(GroovyStubElementTypes.PACKAGE_DEFINITION)) {
      myResult = Spacing.createSpacing(0, 0, mySettings.BLANK_LINES_AFTER_PACKAGE + 1, mySettings.KEEP_LINE_BREAKS, keepBlankLines());
    }
    else if (myType2 == GroovyStubElementTypes.PACKAGE_DEFINITION) {
      myResult = Spacing.createSpacing(0, 0, mySettings.BLANK_LINES_BEFORE_PACKAGE + 1, mySettings.KEEP_LINE_BREAKS, keepBlankLines());
    }
    else if (isLeftOrRight(TokenSets.TYPE_DEFINITIONS)) {
      if (myType1 == GroovyDocElementTypes.GROOVY_DOC_COMMENT) {
        if (isGroovyDocFormattingAllowed()) createLF(true);
      }
      else {
        myResult = Spacing.createSpacing(0, 0, mySettings.BLANK_LINES_AROUND_CLASS + 1, mySettings.KEEP_LINE_BREAKS, keepBlankLines());
      }
    }
    else if (isAfterElementOrSemi(GroovyStubElementTypes.IMPORT) && myType2 != GroovyStubElementTypes.IMPORT) { //after imports
      myResult = Spacing.createSpacing(0, 0, mySettings.BLANK_LINES_AFTER_IMPORTS + 1, mySettings.KEEP_LINE_BREAKS, keepBlankLines());
    }
    else if (myType1 != GroovyStubElementTypes.IMPORT && !isSemiAfter(GroovyStubElementTypes.IMPORT) && myType2 ==
                                                                                                        GroovyStubElementTypes.IMPORT) { //before imports
      myResult = Spacing.createSpacing(0, 0, mySettings.BLANK_LINES_BEFORE_IMPORTS, mySettings.KEEP_LINE_BREAKS, keepBlankLines());
    }
    else if (isAfterElementOrSemi(GroovyStubElementTypes.IMPORT) && myType2 == GroovyStubElementTypes.IMPORT) {
      myResult = Spacing.createSpacing(0, 0, 1, mySettings.KEEP_LINE_BREAKS, keepBlankLines());
    }
    else {
      processClassMembers(null);
    }
  }

  private boolean isAfterElementOrSemi(final IElementType elementType) {
    return myType1 == elementType && myType2 != GroovyTokenTypes.mSEMI || isSemiAfter(elementType);
  }

  private boolean isSemiAfter(@NotNull IElementType statement) {
    return myType1 == GroovyTokenTypes.mSEMI && getStatementTypeBySemi(myChild1) == statement;
  }

  private boolean isSemiAfter(@NotNull TokenSet set) {
    return myType1 == GroovyTokenTypes.mSEMI && set.contains(getStatementTypeBySemi(myChild1));
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

  @Override
  public void visitClosure(@NotNull GrClosableBlock closure) {
    ASTNode rBraceAtTheEnd = GeeseUtil.getClosureRBraceAtTheEnd(myChild1);
    boolean spacesWithinBraces = closure.getParent() instanceof GrStringInjection
                                 ? myGroovySettings.SPACE_WITHIN_GSTRING_INJECTION_BRACES
                                 : mySettings.SPACE_WITHIN_BRACES;
    boolean simpleClosure = shouldHandleAsSimpleClosure(closure, mySettings) || myForbidNewLineInSpacing;
    int spaces = simpleClosure && spacesWithinBraces ? 1 : 0;
    if (myGroovySettings.USE_FLYING_GEESE_BRACES && myType2 == GroovyTokenTypes.mRCURLY && rBraceAtTheEnd != null) {
      String text = rBraceAtTheEnd.getTreeParent().getText();
      if (text.indexOf('\n') < 0) {
        /* the case:
       foo {
         bar {print x}<we are here>}
        */
        myResult = Spacing.createSpacing(1, 1, simpleClosure ? 0 : 1, false, 1);
      }
      else {
        myResult = Spacing.createSpacing(0, 0, 0, true, 100, 0);
      }
    }
    else if (myType1 == GroovyTokenTypes.mLCURLY && myType2 == GroovyTokenTypes.mRCURLY) {  //empty closure
      createSpaceInCode(false);
    }
    else if (myType1 == GroovyTokenTypes.mLCURLY && myType2 != GroovyEmptyStubElementTypes.PARAMETER_LIST && myType2 !=
                                                                                                             GroovyTokenTypes.mCLOSABLE_BLOCK_OP || myType2 ==
                                                                                                                                            GroovyTokenTypes.mRCURLY) { //spaces between statements
      myResult = Spacing.createSpacing(spaces, spaces, simpleClosure ? 0 : 1, mySettings.KEEP_LINE_BREAKS, keepBlankLines());
    }
    else if (myType1 == GroovyTokenTypes.mCLOSABLE_BLOCK_OP) {
      myResult = GroovySpacingProcessorBasic.createDependentSpacingForClosure(mySettings, myGroovySettings, closure, true);
    }
    else if (myType1 == GroovyTokenTypes.mLCURLY) {
      createSpaceInCode(spacesWithinBraces);
    }

  }

  @Override
  public void visitOpenBlock(@NotNull GrOpenBlock block) {
    boolean isMethod = block.getParent() instanceof GrMethod;
    boolean keepInOneLine = myForbidNewLineInSpacing ||
                            isMethod ? mySettings.KEEP_SIMPLE_METHODS_IN_ONE_LINE : mySettings.KEEP_SIMPLE_BLOCKS_IN_ONE_LINE;

    if (myType1 == GroovyTokenTypes.mLCURLY && myType2 == GroovyTokenTypes.mRCURLY) {
      createLF(!keepInOneLine);
    }
    else if (myType1 == GroovyTokenTypes.mLCURLY) {
      if (keepInOneLine) {
        createDependentLFSpacing(true, mySettings.SPACE_WITHIN_BRACES, block.getTextRange());
      }
      else {
        int lineFeedsCount = isMethod ? mySettings.BLANK_LINES_BEFORE_METHOD_BODY + 1 : 1;
        myResult = Spacing.createSpacing(0, 0, lineFeedsCount, mySettings.KEEP_LINE_BREAKS, keepBlankLines());
      }
    }
    else if (myType2 == GroovyTokenTypes.mRCURLY) {
      if (keepInOneLine) {
        createDependentLFSpacing(true, mySettings.SPACE_WITHIN_BRACES, block.getTextRange());
      }
      else {
        createLF(true);
      }
    }
  }

  @Override
  public void visitTypeDefinition(@NotNull GrTypeDefinition typeDefinition) {
    if (myType2 == GroovyEmptyStubElementTypes.CLASS_BODY) {
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
    else if (myType2 == GroovyEmptyStubElementTypes.TYPE_PARAMETER_LIST) {
      createSpaceInCode(false);
    }
    else if (myType2 == GroovyElementTypes.ARGUMENTS) {
      manageSpaceBeforeCallLParenth();
    }
  }

  @Override
  public void visitTypeDefinitionBody(@NotNull GrTypeDefinitionBody typeDefinitionBody) {
    if (myType1 == GroovyTokenTypes.mLCURLY && myType2 == GroovyTokenTypes.mRCURLY) {
      if (mySettings.KEEP_SIMPLE_CLASSES_IN_ONE_LINE) {
        createSpaceInCode(false);
      }
      else {
        createLF(true);
      }
    }
    else if (myType1 == GroovyTokenTypes.mLCURLY) {
      myResult = Spacing.createSpacing(0, 0, mySettings.BLANK_LINES_AFTER_CLASS_HEADER + 1, mySettings.KEEP_LINE_BREAKS, keepBlankLines());
    }
    else if (myType2 == GroovyTokenTypes.mRCURLY) {
      createLF(true);
    }
    else {
      processClassMembers(typeDefinitionBody);
    }
  }

  private void processClassMembers(@Nullable GrTypeDefinitionBody typeDefinitionBody) {
    final boolean isInterface = typeDefinitionBody != null && ((GrTypeDefinition)typeDefinitionBody.getParent()).isInterface();

    if (myType2 == GroovyTokenTypes.mSEMI) return;

    if (typeDefinitionBody != null) { //check variable definitions only inside class body
      if ((myType1 == GroovyStubElementTypes.VARIABLE_DECLARATION || isSemiAfter(GroovyStubElementTypes.VARIABLE_DECLARATION)) && TokenSets.METHOD_DEFS.contains(myType2)) {
        final int minBlankLines = Math.max(
          isInterface ? mySettings.BLANK_LINES_AROUND_METHOD_IN_INTERFACE : mySettings.BLANK_LINES_AROUND_METHOD,
          isInterface ? mySettings.BLANK_LINES_AROUND_FIELD_IN_INTERFACE : mySettings.BLANK_LINES_AROUND_FIELD
        );
        myResult = Spacing.createSpacing(0, 0, minBlankLines + 1, mySettings.KEEP_LINE_BREAKS, keepBlankLines());
        return;
      }
      else if (myType1 == GroovyStubElementTypes.VARIABLE_DECLARATION || isSemiAfter(GroovyStubElementTypes.VARIABLE_DECLARATION) || myType2 ==
                                                                                                                                     GroovyStubElementTypes.VARIABLE_DECLARATION) {
        final int minBlankLines =
          isInterface ? mySettings.BLANK_LINES_AROUND_FIELD_IN_INTERFACE : mySettings.BLANK_LINES_AROUND_FIELD;
        myResult = Spacing.createSpacing(0, 0, minBlankLines + 1, mySettings.KEEP_LINE_BREAKS, keepBlankLines());
        return;
      }
    }

    if (TokenSets.METHOD_DEFS.contains(myType1) || isSemiAfter(TokenSets.METHOD_DEFS) || TokenSets.METHOD_DEFS.contains((myType2))) {
      if (myType1 == GroovyDocElementTypes.GROOVY_DOC_COMMENT) {
        if (isGroovyDocFormattingAllowed()) createLF(true);
      }
      else {
        final int minBlankLines = isInterface ? mySettings.BLANK_LINES_AROUND_METHOD_IN_INTERFACE : mySettings.BLANK_LINES_AROUND_METHOD;
        myResult = Spacing.createSpacing(0, 0, minBlankLines + 1, mySettings.KEEP_LINE_BREAKS, keepBlankLines());
      }
      return;
    }

    if (TokenSets.TYPE_DEFINITIONS.contains(myType1) || isSemiAfter(TokenSets.TYPE_DEFINITIONS) || TokenSets.TYPE_DEFINITIONS.contains((myType2)) ) {
      if (myType1 == GroovyDocElementTypes.GROOVY_DOC_COMMENT) {
        if (isGroovyDocFormattingAllowed()) createLF(true);
      }
      else {
        final int minBlankLines = mySettings.BLANK_LINES_AROUND_CLASS;
        myResult = Spacing.createSpacing(0, 0, minBlankLines + 1, mySettings.KEEP_LINE_BREAKS, keepBlankLines());
      }
    }
  }

  @Override
  public void visitTypeArgumentList(@NotNull GrTypeArgumentList typeArgumentList) {
    if (myType1 == GroovyTokenTypes.mLT || myType2 == GroovyTokenTypes.mGT) {
      createSpaceInCode(false);
    }
  }

  @Override
  public void visitTypeParameterList(@NotNull GrTypeParameterList list) {
    if (myType1 == GroovyTokenTypes.mCOMMA) {
      createSpaceInCode(mySettings.SPACE_AFTER_COMMA_IN_TYPE_ARGUMENTS);
    }
    else if (myType1 == GroovyTokenTypes.mLT && myType2 == GroovyStubElementTypes.TYPE_PARAMETER ||
             myType1 == GroovyStubElementTypes.TYPE_PARAMETER && myType2 == GroovyTokenTypes.mGT) {
      createSpaceInCode(false);
    }
  }

  @Override
  public void visitForInClause(@NotNull GrForInClause forInClause) {
    if (myType1 == GroovyStubElementTypes.PARAMETER && myType2 == GroovyTokenTypes.mCOLON) {
      createSpaceInCode(true);
    }
  }

  @Override
  public void visitCastExpression(@NotNull GrTypeCastExpression typeCastExpression) {
    if (SpacingTokens.LEFT_BRACES.contains(myType1) || SpacingTokens.RIGHT_BRACES.contains(myType2)) {
      createSpaceInCode(mySettings.SPACE_WITHIN_CAST_PARENTHESES);
    }
    else if (myType1 == GroovyTokenTypes.mRPAREN) {
      createSpaceInCode(mySettings.SPACE_AFTER_TYPE_CAST);
    }
  }

  @Override
  public void visitMethod(@NotNull GrMethod method) {
    if (myType2 == PARAMETER_LIST) {
      createSpaceInCode(mySettings.SPACE_BEFORE_METHOD_PARENTHESES);
    }
    else if (myType1 == PARAMETER_LIST && myType2 == GroovyStubElementTypes.THROWS_CLAUSE) {
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
    else if (myType2 == GroovyEmptyStubElementTypes.TYPE_PARAMETER_LIST) {
      createSpaceInCode(true);
    }
  }

  @Override
  public void visitRecordDefinition(@NotNull GrRecordDefinition recordDefinition) {
    if (myType2 == PARAMETER_LIST) {
      createSpaceInCode(myGroovySettings.SPACE_BEFORE_RECORD_PARENTHESES);
    }
    visitTypeDefinition(recordDefinition);
  }

  @Override
  public void visitParameterList(@NotNull GrParameterList parameterList) {
    processParentheses(
      mySettings.SPACE_WITHIN_METHOD_PARENTHESES,
      mySettings.SPACE_WITHIN_EMPTY_METHOD_PARENTHESES,
      mySettings.METHOD_PARAMETERS_LPAREN_ON_NEXT_LINE,
      mySettings.METHOD_PARAMETERS_RPAREN_ON_NEXT_LINE
    );
  }

  private boolean processBrackets(@NotNull Boolean spaceWithin,
                                  @Nullable Boolean spaceWithinEmpty,
                                  @Nullable Boolean leftLF,
                                  @Nullable Boolean rightLF) {
    return processParentheses(spaceWithin, spaceWithinEmpty, leftLF, rightLF, T_LBRACK, T_RBRACK);
  }

  private boolean processParentheses(@NotNull Boolean spaceWithin,
                                     @Nullable Boolean spaceWithinEmpty,
                                     @Nullable Boolean leftLF,
                                     @Nullable Boolean rightLF) {
    return processParentheses(spaceWithin, spaceWithinEmpty, leftLF, rightLF, T_LPAREN, T_RPAREN);
  }

  private boolean processBraces(@NotNull Boolean spaceWithin,
                                @Nullable Boolean spaceWithinEmpty,
                                @Nullable Boolean leftLF,
                                @Nullable Boolean rightLF) {
    return processParentheses(spaceWithin, spaceWithinEmpty, leftLF, rightLF, T_LBRACE, T_RBRACE);
  }

  private boolean processParentheses(@NotNull Boolean spaceWithin,
                                     @Nullable Boolean spaceWithinEmpty,
                                     @Nullable Boolean leftLF,
                                     @Nullable Boolean rightLF,
                                     @NotNull IElementType leftType,
                                     @NotNull IElementType rightType) {
    if (myType1 == leftType && myType2 == rightType && spaceWithinEmpty != null) {
      createSpaceInCode(spaceWithinEmpty);
      return true;
    }
    else if (myType1 == leftType) {
      final ASTNode rparenth = findFrom(myChild1, rightType, true);
      if (rparenth == null || leftLF == null) {
        createSpaceInCode(spaceWithin);
      }
      else {
        final TextRange range = new TextRange(myChild1.getStartOffset(), rparenth.getTextRange().getEndOffset());
        createDependentLFSpacing(leftLF, spaceWithin, range);
      }
      return true;
    }
    else if (myType2 == rightType) {
      final ASTNode lparenth = findFrom(myChild1, leftType, false);
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

  private boolean isGroovyDocFormattingAllowed() {
    return myGroovySettings.ENABLE_GROOVYDOC_FORMATTING;
  }

  @Override
  public void visitAnnotationMethod(@NotNull GrAnnotationMethod annotationMethod) {
    if (myType2 == GroovyTokenTypes.kDEFAULT) {
      createSpaceInCode(true);
    }
    else {
      super.visitAnnotationMethod(annotationMethod);
    }
  }

  @Override
  public void visitDocMethodReference(@NotNull GrDocMethodReference reference) {
    visitDocMember();
  }

  @Override
  public void visitDocFieldReference(@NotNull GrDocFieldReference reference) {
    visitDocMember();
  }

  private void visitDocMember() {
    if (isGroovyDocFormattingAllowed()) {
      createSpaceProperty(false, false, 0);
    } else {
      createLazySpace();
    }
  }

  @Override
  public void visitDocMethodParameterList(@NotNull GrDocMethodParams params) {
    if (!isGroovyDocFormattingAllowed()) {
      createLazySpace();
      return;
    }
    if (myType1 == GroovyDocTokenTypes.mGDOC_TAG_VALUE_LPAREN || myType2 == GroovyDocTokenTypes.mGDOC_TAG_VALUE_RPAREN || myType2 ==
                                                                                                                          GroovyDocTokenTypes.mGDOC_TAG_VALUE_COMMA) {
      createSpaceProperty(false, false, 0);
    }
    else {
      createSpaceInCode(true);
    }
  }

  @Override
  public void visitDocMethodParameter(@NotNull GrDocMethodParameter parameter) {
    if (!isGroovyDocFormattingAllowed()) {
      createLazySpace();
      return;
    }
    if (myChild1.getTreePrev() == null) {
      createSpaceInCode(true);
    }
  }

  @Override
  public void visitWhileStatement(@NotNull GrWhileStatement statement) {
    if (myType2 == GroovyTokenTypes.mLPAREN) {
      createSpaceInCode(mySettings.SPACE_BEFORE_WHILE_PARENTHESES);
    }
    else if (isWithinParentheses()) {
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

  @Override
  public void visitDoWhileStatement(@NotNull GrDoWhileStatement statement) {
    if (myType1 == KW_DO) {
      createSpaceBeforeLBrace(
        mySettings.SPACE_BEFORE_DO_LBRACE,
        mySettings.BRACE_STYLE,
        myChild1.getTextRange(),
        mySettings.KEEP_SIMPLE_BLOCKS_IN_ONE_LINE
      );
    }
    else if (myType2 == KW_WHILE) {
      if (mySettings.WHILE_ON_NEW_LINE) {
        createLF(true);
      }
      else {
        createSpaceInCode(mySettings.SPACE_BEFORE_WHILE_KEYWORD);
      }
    }
    else if (myType2 == T_LPAREN) {
      createSpaceInCode(mySettings.SPACE_BEFORE_WHILE_PARENTHESES);
    }
    else if (isWithinParentheses()) {
      createSpaceInCode(mySettings.SPACE_WITHIN_WHILE_PARENTHESES);
    }
    else {
      createSpacingBeforeElementInsideControlStatement();
    }
  }

  @Override
  public void visitCatchClause(@NotNull GrCatchClause catchClause) {
    if (isOpenBlock(myType2)) {
      createSpaceBeforeLBrace(mySettings.SPACE_BEFORE_CATCH_LBRACE, mySettings.BRACE_STYLE, null,
                              mySettings.KEEP_SIMPLE_BLOCKS_IN_ONE_LINE);
    }
    else if (myType2 == GroovyTokenTypes.mLPAREN) {
      createSpaceInCode(mySettings.SPACE_BEFORE_CATCH_PARENTHESES);
    }
    else if (isWithinParentheses()) {
      createSpaceInCode(mySettings.SPACE_WITHIN_CATCH_PARENTHESES);
    }
  }

  @Override
  public void visitFinallyClause(@NotNull GrFinallyClause catchClause) {
    if (isOpenBlock(myType2)) {
      createSpaceBeforeLBrace(mySettings.SPACE_BEFORE_FINALLY_LBRACE, mySettings.BRACE_STYLE, null,
                              mySettings.KEEP_SIMPLE_BLOCKS_IN_ONE_LINE);
    }
  }

  @Override
  public void visitTryStatement(@NotNull GrTryCatchStatement tryCatchStatement) {
    if (myType2 == TRY_RESOURCE_LIST) {
      createSpaceInCode(mySettings.SPACE_BEFORE_TRY_PARENTHESES);
    }
    else if (myType2 == GroovyElementTypes.FINALLY_CLAUSE) {
      processTryOnNewLineCondition(mySettings.FINALLY_ON_NEW_LINE, mySettings.SPACE_BEFORE_FINALLY_KEYWORD);
    }
    else if (isOpenBlock(myType2)) {
      createSpaceBeforeLBrace(mySettings.SPACE_BEFORE_TRY_LBRACE, mySettings.BRACE_STYLE, null, mySettings.KEEP_SIMPLE_BLOCKS_IN_ONE_LINE);
    }
    else if (myType2 == GroovyElementTypes.CATCH_CLAUSE) {
      processTryOnNewLineCondition(mySettings.CATCH_ON_NEW_LINE, mySettings.SPACE_BEFORE_CATCH_KEYWORD);
    }
  }

  @Override
  public void visitTryResourceList(@NotNull GrTryResourceList resourceList) {
    processParentheses(
      mySettings.SPACE_WITHIN_TRY_PARENTHESES,
      null,
      mySettings.RESOURCE_LIST_LPAREN_ON_NEXT_LINE,
      mySettings.RESOURCE_LIST_RPAREN_ON_NEXT_LINE
    );
  }

  @Override
  public void visitAssignmentExpression(@NotNull GrAssignmentExpression expression) {
    if (ASSIGNMENTS.contains(myType1) || ASSIGNMENTS.contains(myType2)) {
      createSpaceInCode(mySettings.SPACE_AROUND_ASSIGNMENT_OPERATORS);
    }
  }

  @Override
  public void visitBinaryExpression(@NotNull GrBinaryExpression expression) {
    boolean spaceAround = isLeftOrRight(LOGICAL_OPERATORS) ? mySettings.SPACE_AROUND_LOGICAL_OPERATORS :
                          isLeftOrRight(EQUALITY_OPERATORS) ? mySettings.SPACE_AROUND_EQUALITY_OPERATORS :
                          isLeftOrRight(RELATIONAL_OPERATORS) ? mySettings.SPACE_AROUND_RELATIONAL_OPERATORS :
                          isLeftOrRight(BITWISE_OPERATORS) ? mySettings.SPACE_AROUND_BITWISE_OPERATORS :
                          isLeftOrRight(ADDITIVE_OPERATORS) ? mySettings.SPACE_AROUND_ADDITIVE_OPERATORS :
                          isLeftOrRight(MULTIPLICATIVE_OPERATORS) ? mySettings.SPACE_AROUND_MULTIPLICATIVE_OPERATORS :
                          isLeftOrRight(SHIFT_OPERATORS) ? mySettings.SPACE_AROUND_SHIFT_OPERATORS :
                          isLeftOrRight(REGEX_OPERATORS) ? myGroovySettings.SPACE_AROUND_REGEX_OPERATORS :
                          isLeftOrRight(KW_IN) || isLeftOrRight(T_NOT_IN);
    if ((myType2 == GroovyTokenTypes.mBOR || myType2 == GroovyTokenTypes.mLOR) && GroovyBlockGenerator.isTablePart(expression) &&
        SpecificationKt.isInsideSpecification(expression)) {
      createSpaceBeforeSpockDelimiter(spaceAround);
    }
    else if (BINARY_OPERATORS.contains(myType2)) {
      createDependentLFSpacing(mySettings.BINARY_OPERATION_SIGN_ON_NEXT_LINE, spaceAround, expression.getTextRange());
    }
    else {
      createSpaceInCode(spaceAround);
    }
  }

  private void createSpaceBeforeSpockDelimiter(boolean spaceAround) {
    if (!myChild1.textContains('\n')) {
      createSpaceInCode(spaceAround);
      return;
    }
    String text = myChild1.getText();
    int maxLength = 0;
    int lastLength = 0;
    for (Iterator<String> line = text.lines().iterator(); line.hasNext();) {
      String currentLine = line.next();
      int length = currentLine.length();
      if (!line.hasNext()) {
        lastLength = length;
      }
      if (length > maxLength) {
        maxLength = length;
      }
    }
    int spaces = maxLength + (spaceAround ? 1 : 0) - lastLength;
    myResult = Spacing.createSpacing(spaces, spaces, 0, mySettings.KEEP_LINE_BREAKS, keepBlankLines());
  }

  private boolean isLeftOrRight(TokenSet operators) {
    return operators.contains(myType1) || operators.contains(myType2);
  }

  private boolean isLeftOrRight(IElementType type) {
    return myType1 == type || myType2 == type;
  }

  @Override
  public void visitUnaryExpression(@NotNull GrUnaryExpression expression) {
    if (!expression.isPostfix() && expression.getOperationToken() == myChild1 ||
        expression.isPostfix() && expression.getOperationToken() == myChild2) {
      createSpaceInCode(mySettings.SPACE_AROUND_UNARY_OPERATOR);
    }
  }

  private void visitSwitchElement() {
    if (myType1 == GroovyTokenTypes.kSWITCH && myType2 == GroovyTokenTypes.mLPAREN) {
      createSpaceInCode(mySettings.SPACE_BEFORE_SWITCH_PARENTHESES);
    }
    else if (isWithinParentheses()) {
      createSpaceInCode(mySettings.SPACE_WITHIN_SWITCH_PARENTHESES);
    }
    else if (myType2 == GroovyTokenTypes.mLCURLY) {
      createSpaceBeforeLBrace(mySettings.SPACE_BEFORE_SWITCH_LBRACE, mySettings.BRACE_STYLE, null,
                              mySettings.KEEP_SIMPLE_BLOCKS_IN_ONE_LINE);
    }
    else if (isWithinBraces()) {
      createLF(true);
    }
  }

  @Override
  public void visitSwitchStatement(@NotNull GrSwitchStatement switchStatement) {
    visitSwitchElement();
  }

  @Override
  public void visitSwitchExpression(@NotNull GrSwitchExpression switchExpression) {
    visitSwitchElement();
  }

  @Override
  public void visitSynchronizedStatement(@NotNull GrSynchronizedStatement synchronizedStatement) {
    if (myType1 == GroovyTokenTypes.kSYNCHRONIZED || myType2 == GroovyTokenTypes.mLPAREN) {
      createSpaceInCode(mySettings.SPACE_BEFORE_SYNCHRONIZED_PARENTHESES);
    } else if (isWithinParentheses()) {
      createSpaceInCode(mySettings.SPACE_WITHIN_SYNCHRONIZED_PARENTHESES);
    } else if (isOpenBlock(myType2)) {
      createSpaceBeforeLBrace(mySettings.SPACE_BEFORE_SYNCHRONIZED_LBRACE, mySettings.BRACE_STYLE, null,
                              mySettings.KEEP_SIMPLE_BLOCKS_IN_ONE_LINE);
    }

  }

  @Override
  public void visitDocComment(@NotNull GrDocComment comment) {
    if (!isGroovyDocFormattingAllowed()) {
      createLazySpace();
      return;
    }
    if (myType1 == GroovyDocElementTypes.GDOC_TAG &&
        myType2 == GroovyDocElementTypes.GDOC_TAG &&
        mySettings.getRootSettings().JD_LEADING_ASTERISKS_ARE_ENABLED) {
      IElementType type = myChild1.getLastChildNode().getElementType();
      if (type == GroovyDocTokenTypes.mGDOC_ASTERISKS) {
        createSpaceInCode(true);
      }
    }

    if (myType1 == GroovyDocTokenTypes.mGDOC_COMMENT_START && myType2 == GroovyDocTokenTypes.mGDOC_COMMENT_DATA ||
        myType1 == GroovyDocTokenTypes.mGDOC_COMMENT_DATA &&  myType2 == GroovyDocTokenTypes.mGDOC_COMMENT_END ||
        myType1 == GroovyDocTokenTypes.mGDOC_ASTERISKS &&     myType2 == GroovyDocTokenTypes.mGDOC_COMMENT_END) {
      createLazySpace();
    }
  }

  private void createLazySpace() {
    myResult = Spacing.createSpacing(0, Integer.MAX_VALUE, 0, mySettings.KEEP_LINE_BREAKS, keepBlankLines());
  }

  @Override
  public void visitDocTag(@NotNull GrDocTag docTag) {
    if (!isGroovyDocFormattingAllowed()) {
      createLazySpace();
      return;
    }
    if ((myType1 == GroovyDocTokenTypes.mGDOC_INLINE_TAG_START && myChild1 == docTag.getFirstChild()) ||
        (myType2 == GroovyDocTokenTypes.mGDOC_INLINE_TAG_END && myChild2 == docTag.getLastChild())) {
      createSpaceProperty(false, false, 0);
    }
    else if (myType1 == GroovyDocTokenTypes.mGDOC_TAG_VALUE_TOKEN && myType2 != GroovyDocTokenTypes.mGDOC_COMMENT_DATA) {
      createSpaceProperty(false, false, 0);
    }
    else if (myType1 != GroovyDocTokenTypes.mGDOC_TAG_NAME && myType2 == GroovyDocTokenTypes.mGDOC_TAG_VALUE_TOKEN) {
      createSpaceProperty(false, false, 0);
    }
  }

  @Override
  public void visitNamedArgument(@NotNull GrNamedArgument argument) {
    if (myType1 == GroovyElementTypes.ARGUMENT_LABEL && myType2 == GroovyTokenTypes.mCOLON) {
      createSpaceInCode(myGroovySettings.SPACE_IN_NAMED_ARGUMENT_BEFORE_COLON);
    }
    else if (myType1 == GroovyTokenTypes.mCOLON) {
      createSpaceInCode(myGroovySettings.SPACE_IN_NAMED_ARGUMENT);
    }
  }

  @Override
  public void visitListOrMap(@NotNull GrListOrMap listOrMap) {
    if (isWithinBrackets()) {
      createSpaceInCode(myGroovySettings.SPACE_WITHIN_LIST_OR_MAP);
    }
  }

  @Override
  public void visitParenthesizedExpression(@NotNull GrParenthesizedExpression expression) {
    processParentheses(
      mySettings.SPACE_WITHIN_PARENTHESES,
      null,
      mySettings.PARENTHESES_EXPRESSION_LPAREN_WRAP,
      mySettings.PARENTHESES_EXPRESSION_RPAREN_WRAP
    );
  }

  @Override
  public void visitAnnotationArrayInitializer(@NotNull GrAnnotationArrayInitializer arrayInitializer) {
    if (isWithinBrackets()) {
      createSpaceInCode(mySettings.SPACE_WITHIN_BRACKETS);
    }
  }

  @Override
  public void visitAssertStatement(@NotNull GrAssertStatement assertStatement) {
    if (myType2 == T_COLON || myType2 == T_COMMA) {
      createSpaceInCode(myGroovySettings.SPACE_BEFORE_ASSERT_SEPARATOR);
    }
    else if (myType1 == T_COLON || myType1 == T_COMMA) {
      createSpaceInCode(myGroovySettings.SPACE_AFTER_ASSERT_SEPARATOR);
    }
  }

  @Override
  public void visitIfStatement(@NotNull GrIfStatement ifStatement) {
    if (myType2 == GroovyTokenTypes.kELSE) {
      if (!isOpenBlock(myType1) && myType1 != GroovyElementTypes.BLOCK_STATEMENT) {
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
    else if (myType1 == GroovyTokenTypes.kELSE) {
      if (myType2 == GroovyElementTypes.IF_STATEMENT) {
        if (mySettings.SPECIAL_ELSE_IF_TREATMENT) {
          createSpaceProperty(true, false, 0);
        }
        else {
          createLF(true);
        }
      }
      else {
        if (myType2 == GroovyElementTypes.BLOCK_STATEMENT || isOpenBlock(myType2)) {
          createSpaceBeforeLBrace(mySettings.SPACE_BEFORE_ELSE_LBRACE, mySettings.BRACE_STYLE, null,
                                  mySettings.KEEP_SIMPLE_BLOCKS_IN_ONE_LINE);
        }
        else {
          createSpacingBeforeElementInsideControlStatement();
        }
      }
    }
    else if (myType2 == GroovyElementTypes.BLOCK_STATEMENT || isOpenBlock(myType2)) {
      boolean space = myChild2.getPsi() == ((GrIfStatement)myParent).getElseBranch()
                      ? mySettings.SPACE_BEFORE_ELSE_LBRACE
                      : mySettings.SPACE_BEFORE_IF_LBRACE;
      createSpaceBeforeLBrace(space, mySettings.BRACE_STYLE,
                              new TextRange(myParent.getTextRange().getStartOffset(), myChild1.getTextRange().getEndOffset()),
                              mySettings.KEEP_SIMPLE_BLOCKS_IN_ONE_LINE);
    }
    else if (myType2 == GroovyTokenTypes.mLPAREN) {
      createSpaceInCode(mySettings.SPACE_BEFORE_IF_PARENTHESES);
    }
    else if (isWithinParentheses()) {
      createSpaceInCode(mySettings.SPACE_WITHIN_IF_PARENTHESES);
    }
    else if (((GrIfStatement)myParent).getThenBranch() == myChild2.getPsi()) {
      createSpacingBeforeElementInsideControlStatement();
    }
  }

  @Override
  public void visitForStatement(@NotNull GrForStatement forStatement) {
    if (myType2 == GroovyTokenTypes.mLPAREN) {
      createSpaceInCode(mySettings.SPACE_BEFORE_FOR_PARENTHESES);
    }
    else if (myType2 == GroovyElementTypes.BLOCK_STATEMENT || isOpenBlock(myType2)) {
      if (myType2 == GroovyElementTypes.BLOCK_STATEMENT) {
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
    else if (myType1 == GroovyTokenTypes.mRPAREN) {
      createSpacingBeforeElementInsideControlStatement();
    }
    else {
      processParentheses(
        mySettings.SPACE_WITHIN_FOR_PARENTHESES,
        null,
        mySettings.FOR_STATEMENT_LPAREN_ON_NEXT_LINE,
        mySettings.FOR_STATEMENT_RPAREN_ON_NEXT_LINE
      );
    }
  }

  @Override
  public void visitArrayInitializer(@NotNull GrArrayInitializer arrayInitializer) {
    processBraces(
      mySettings.SPACE_WITHIN_ARRAY_INITIALIZER_BRACES,
      mySettings.SPACE_WITHIN_EMPTY_ARRAY_INITIALIZER_BRACES,
      mySettings.ARRAY_INITIALIZER_LBRACE_ON_NEXT_LINE,
      mySettings.ARRAY_INITIALIZER_RBRACE_ON_NEXT_LINE
    );
  }

  private static boolean isOpenBlock(IElementType type) {
    return type instanceof GrBlockElementType || type == GroovyElementTypes.CONSTRUCTOR_BODY;
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
    if (mySettings.KEEP_CONTROL_STATEMENT_IN_ONE_LINE && myType1 != GroovyTokenTypes.mSL_COMMENT) {
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
    createSpaceProperty(space, mySettings.KEEP_LINE_BREAKS, keepBlankLines());
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
    if (prev != null && prev.getElementType() == GroovyTokenTypes.mSL_COMMENT) {
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
    if (myType2 == GroovyTokenTypes.mRCURLY) {
      return mySettings.KEEP_BLANK_LINES_BEFORE_RBRACE;
    }
    else if (myParent instanceof GrTypeDefinitionBody) {
      return mySettings.KEEP_BLANK_LINES_IN_DECLARATIONS;
    }
    else {
      return mySettings.KEEP_BLANK_LINES_IN_CODE;
    }
  }

  @Contract(pure = true)
  private boolean isWithinParentheses() {
    return isWithin(T_LPAREN, T_RPAREN);
  }

  @Contract(pure = true)
  private boolean isWithinBraces() {
    return isWithin(T_LBRACE, T_RBRACE);
  }

  @Contract(pure = true)
  private boolean isWithinBrackets() {
    return isWithin(T_LBRACK, T_RBRACK);
  }

  @Contract(pure = true)
  private boolean isWithin(IElementType left, IElementType right) {
    return myType1 == left || myType2 == right;
  }

  static boolean isWhiteSpace(final ASTNode node) {
    return node != null && (PsiImplUtil.isWhiteSpaceOrNls(node) || node.getTextLength() == 0);
  }

  public static Spacing getSpacing(@Nullable Block child1, @NotNull Block child2, FormattingContext context) {
    if (child1 instanceof SyntheticGroovyBlock) return getSpacing(((SyntheticGroovyBlock)child1).getLastChild(), child2, context);
    if (child2 instanceof SyntheticGroovyBlock) return getSpacing(child1, ((SyntheticGroovyBlock)child2).getFirstChild(), context);

    if (child1 instanceof GroovyBlock && child2 instanceof GroovyBlock) {
      if (((GroovyBlock)child1).getNode() == ((GroovyBlock)child2).getNode()) {
        return Spacing.getReadOnlySpacing();
      }

      Spacing spacing = new GroovySpacingProcessor(((GroovyBlock)child1), (GroovyBlock)child2, context).getSpacing();
      if (spacing != null) {
        return spacing;
      }
      return GroovySpacingProcessorBasic.getSpacing(((GroovyBlock)child1), ((GroovyBlock)child2), context);
    }
    return null;
  }
}

