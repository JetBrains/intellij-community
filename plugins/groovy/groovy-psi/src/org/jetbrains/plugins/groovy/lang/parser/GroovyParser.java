// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.plugins.groovy.lang.parser;

import com.intellij.lang.PsiBuilder;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.parser.parsing.auxiliary.Separators;
import org.jetbrains.plugins.groovy.lang.parser.parsing.auxiliary.modifiers.Modifiers;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.*;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.blocks.OpenOrClosableBlock;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.declaration.Declaration;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.expressions.AssignmentExpression;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.expressions.ConditionalExpression;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.expressions.ExpressionStatement;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.expressions.arithmetic.PathExpression;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.imports.ImportStatement;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.typeDefinitions.TypeDefinition;
import org.jetbrains.plugins.groovy.lang.parser.parsing.util.ParserUtils;

/**
 * Parser for Groovy script files
 *
 * @author ilyas, Dmitry.Krasilschikov
 */
public class GroovyParser extends GroovyGeneratedParser {

  public static final TokenSet RCURLY_ONLY = TokenSet.create(GroovyTokenTypes.mRCURLY);
  public static final TokenSet CASE_SECTION_END = TokenSet.create(GroovyTokenTypes.kCASE, GroovyTokenTypes.kDEFAULT,
                                                                  GroovyTokenTypes.mRCURLY);

  public boolean parseDeep() {
    return false;
  }

  public static void parseExpression(PsiBuilder builder) {
    ExpressionStatement.argParse(builder, new GroovyParser());
  }

  public boolean parseForStatement(PsiBuilder builder) {
    PsiBuilder.Marker marker = builder.mark();

    ParserUtils.getToken(builder, GroovyTokenTypes.kFOR);
    if (!ParserUtils.getToken(builder, GroovyTokenTypes.mLPAREN, GroovyBundle.message("lparen.expected"))) {
      marker.done(GroovyElementTypes.FOR_STATEMENT);
      return true;
    }
    if (!ForStatement.forClauseParse(builder, this)) {
      builder.error(GroovyBundle.message("for.clause.expected"));
      marker.done(GroovyElementTypes.FOR_STATEMENT);
      return true;
    }

    ParserUtils.getToken(builder, GroovyTokenTypes.mNLS);

    if (!ParserUtils.getToken(builder, GroovyTokenTypes.mRPAREN, GroovyBundle.message("rparen.expected"))) {
      ParserUtils.getToken(builder, GroovyTokenTypes.mNLS);
      marker.done(GroovyElementTypes.FOR_STATEMENT);
      return true;
    }

    PsiBuilder.Marker warn = builder.mark();
    ParserUtils.getToken(builder, GroovyTokenTypes.mNLS);

    if (parseExtendedStatement(builder)) {
      warn.rollbackTo();
      marker.done(GroovyElementTypes.FOR_STATEMENT);
      return true;
    }

    if (parseStatement(builder, true)) {
      warn.drop();
    }
    else {
      warn.rollbackTo();
      builder.error(GroovyBundle.message("statement.expected"));
    }
    marker.done(GroovyElementTypes.FOR_STATEMENT);
    return true;
  }

  public boolean parseIfStatement(PsiBuilder builder) {
    //allow error messages
    PsiBuilder.Marker ifStmtMarker = builder.mark();
    if (!ParserUtils.getToken(builder, GroovyTokenTypes.kIF)) {
      ifStmtMarker.rollbackTo();
      builder.error(GroovyBundle.message("if.expected"));
      return false;
    }

    if (!ParserUtils.getToken(builder, GroovyTokenTypes.mLPAREN, GroovyBundle.message("lparen.expected"))) {
      ifStmtMarker.done(GroovyElementTypes.IF_STATEMENT);
      return true;
    }

    if (!ConditionalExpression.parse(builder, this)) {
      builder.error(GroovyBundle.message("expression.expected"));
    }

    ParserUtils.getToken(builder, GroovyTokenTypes.mNLS);
    ParserUtils.getToken(builder, GroovyTokenTypes.mRPAREN, GroovyBundle.message("rparen.expected"));

    if (!parseBranch(builder)) {
      ifStmtMarker.done(GroovyElementTypes.IF_STATEMENT);
      return true;
    }

    PsiBuilder.Marker rb = builder.mark();
    if (GroovyTokenTypes.kELSE.equals(builder.getTokenType()) ||
        (Separators.parse(builder) && builder.getTokenType() == GroovyTokenTypes.kELSE)) {
      rb.drop();
      ParserUtils.getToken(builder, GroovyTokenTypes.kELSE);

      parseBranch(builder);
    }
    else {
      rb.rollbackTo();
    }

    ifStmtMarker.done(GroovyElementTypes.IF_STATEMENT);
    return true;
  }

  public void parseSwitchCaseList(PsiBuilder builder) {
    if (parseGenericStatement(builder, CASE_SECTION_END)) {
      parseCodeBlock(builder, CASE_SECTION_END);
    }
  }

  //gsp directives, scriptlets and such
  protected boolean isExtendedSeparator(@Nullable final IElementType tokenType) {
    return false;
  }

  //gsp template statement, for example
  protected boolean parseExtendedStatement(PsiBuilder builder) {
    return false;
  }

  public boolean parseWhileStatement(PsiBuilder builder) {

    PsiBuilder.Marker marker = builder.mark();

    ParserUtils.getToken(builder, GroovyTokenTypes.kWHILE);

    if (!ParserUtils.getToken(builder, GroovyTokenTypes.mLPAREN, GroovyBundle.message("lparen.expected"))) {
      marker.done(GroovyElementTypes.WHILE_STATEMENT);
      return true;
    }

    if (!ExpressionStatement.argParse(builder, this)) {
      builder.error(GroovyBundle.message("expression.expected"));
    }

    ParserUtils.getToken(builder, GroovyTokenTypes.mNLS);

    if (!ParserUtils.getToken(builder, GroovyTokenTypes.mRPAREN, GroovyBundle.message("rparen.expected"))) {
      marker.done(GroovyElementTypes.WHILE_STATEMENT);
      return true;
    }

    parseBranch(builder);
    marker.done(GroovyElementTypes.WHILE_STATEMENT);
    return true;
  }

  private boolean parseBranch(@NotNull PsiBuilder builder) {
    PsiBuilder.Marker warn = builder.mark();
    ParserUtils.getToken(builder, GroovyTokenTypes.mNLS);

    if (!parseStatement(builder, true) && !parseExtendedStatement(builder)) {
      warn.rollbackTo();
      builder.error(GroovyBundle.message("statement.expected"));
      return false;
    }
    else {
      warn.drop();
      return true;
    }
  }

  public void parseBlockBody(PsiBuilder builder) {
    skipSeparators(builder);
    parseBlockBodyWithoutSkippingSeparators(builder);
  }

  public void parseBlockBodyWithoutSkippingSeparators(PsiBuilder builder) {
    parseCodeBlock(builder, RCURLY_ONLY);
    ParserUtils.getToken(builder, GroovyTokenTypes.mNLS);
  }

  private void parseCodeBlock(PsiBuilder builder, TokenSet until) {
    while (true) {
      if (builder.eof() || until.contains(builder.getTokenType())) break;
      if (!parseGenericStatement(builder, until)) break;
    }
  }

  private boolean parseGenericStatement(PsiBuilder builder, TokenSet until) {
    boolean plainStatement = parseStatement(builder, false);

    if (plainStatement || parseExtendedStatement(builder)) {
      if (parseSeparatorsWithoutLastNls(builder, plainStatement, until)) {
        return false;
      }
    }
    else {
      builder.error(GroovyBundle.message("wrong.statement"));
      assert builder.getTokenType() != GroovyTokenTypes.mLCURLY && builder.getTokenType() != GroovyTokenTypes.mRCURLY;
      builder.advanceLexer();
    }
    return true;
  }

  private boolean parseSeparatorsWithoutLastNls(PsiBuilder builder, boolean requireSeparator, TokenSet until) {
    boolean hasSeparator = false;
    while (true) {
      while (builder.getTokenType() == GroovyTokenTypes.mSEMI || isExtendedSeparator(builder.getTokenType())) {
        hasSeparator = true;
        builder.advanceLexer();
      }

      if (builder.getTokenType() == GroovyTokenTypes.mNLS) {
        PsiBuilder.Marker beforeNls = builder.mark();
        hasSeparator = true;
        builder.advanceLexer();
        if (builder.eof() || until.contains(builder.getTokenType())) {
          beforeNls.rollbackTo();
          return true;
        }
        beforeNls.drop();
      }
      else {
        break;
      }
    }
    if (builder.eof() || until.contains(builder.getTokenType())) {
      return true;
    }
    if (requireSeparator && !hasSeparator) {
      builder.error(GroovyBundle.message("separator.or.rcurly.expected"));
    }
    return false;
  }

  private boolean skipSeparators(PsiBuilder builder) {
    boolean hasSeparators = false;
    while (builder.getTokenType() == GroovyTokenTypes.mSEMI || isExtendedSeparator(builder.getTokenType()) || builder.getTokenType() ==
                                                                                                              GroovyTokenTypes.mNLS) {
      hasSeparators = true;
      builder.advanceLexer();
    }
    return hasSeparators;
  }

  public boolean parseStatement(PsiBuilder builder, boolean isBlockStatementNeeded) {
    if (isBlockStatementNeeded && GroovyTokenTypes.mLCURLY.equals(builder.getTokenType())) {
      final PsiBuilder.Marker marker = builder.mark();
      OpenOrClosableBlock.parseOpenBlockDeep(builder, this);
      marker.done(GroovyElementTypes.BLOCK_STATEMENT);
      return true;
    }

    if (isBlockStatementNeeded && GroovyTokenTypes.mSEMI == builder.getTokenType()) {
      return true;
    }

    if (GroovyTokenTypes.kIMPORT.equals(builder.getTokenType())) {
      PsiBuilder.Marker marker = builder.mark();
      ImportStatement.parse(builder, this);
      marker.error(GroovyBundle.message("import.not.allowed"));
      return true;
    }

    if (GroovyTokenTypes.kIF.equals(builder.getTokenType())) {
      return parseIfStatement(builder);
    }
    if (GroovyTokenTypes.kSWITCH.equals(builder.getTokenType())) {
      SwitchStatement.parseSwitch(builder, this);
      return true;
    }
    if (GroovyTokenTypes.kTRY.equals(builder.getTokenType())) {
      return TryCatchStatement.parse(builder, this);
    }
    if (GroovyTokenTypes.kWHILE.equals(builder.getTokenType())) {
      return parseWhileStatement(builder);
    }
    if (GroovyTokenTypes.kFOR.equals(builder.getTokenType())) {
      return parseForStatement(builder);
    }
    if (ParserUtils.lookAhead(builder, GroovyTokenTypes.kSYNCHRONIZED, GroovyTokenTypes.mLPAREN)) {
      PsiBuilder.Marker synMarker = builder.mark();
      if (SynchronizedStatement.parse(builder, this)) {
        synMarker.drop();
        return true;
      }
      else {
        synMarker.rollbackTo();
      }
    }

    // Possible errors
    if (GroovyTokenTypes.kELSE.equals(builder.getTokenType())) {
      ParserUtils.wrapError(builder, GroovyBundle.message("else.without.if"));
      parseStatement(builder, true);
      return true;
    }
    if (GroovyTokenTypes.kCATCH.equals(builder.getTokenType())) {
      ParserUtils.wrapError(builder, GroovyBundle.message("catch.without.try"));
      parseStatement(builder, false);
      return true;
    }
    if (GroovyTokenTypes.kFINALLY.equals(builder.getTokenType())) {
      ParserUtils.wrapError(builder, GroovyBundle.message("finally.without.try"));
      parseStatement(builder, false);
      return true;
    }
    if (GroovyTokenTypes.kCASE.equals(builder.getTokenType())) {
      PsiBuilder.Marker marker = builder.mark();
      SwitchStatement.parseCaseLabel(builder, this);
      marker.error(GroovyBundle.message("case.without.switch"));
      parseStatement(builder, false);
      return true;
    }
    if (GroovyTokenTypes.kDEFAULT.equals(builder.getTokenType())) {
      PsiBuilder.Marker marker = builder.mark();
      SwitchStatement.parseCaseLabel(builder, this);
      marker.error(GroovyBundle.message("default.without.switch"));
      parseStatement(builder, false);
      return true;
    }

    if (BranchStatement.BRANCH_KEYWORDS.contains(builder.getTokenType())) {
      return BranchStatement.parse(builder, this);
    }
    if (parseLabeledStatement(builder)) {
      return true;
    }

    if (parseDeclaration(builder, false, false, null)) return true;

    return AssignmentExpression.parse(builder, this, true);
  }

  /**
   * parses imports (marks them as not allowed), type definitions, methods, variables or fields (if isInClass), initializers (if isInClass), constructors
   * with corresponding typeDefinitionName
   * <p/>
   * If non of preceding elements was found rolls back and return false
   */
  public boolean parseDeclaration(@NotNull PsiBuilder builder,
                                  boolean isInClass,
                                  boolean isInAnnotation,
                                  @Nullable String typeDefinitionName) {
    PsiBuilder.Marker declMarker = builder.mark();
    boolean modifiersParsed = Modifiers.parse(builder, this);

    if (GroovyTokenTypes.kIMPORT == builder.getTokenType()) {
      final PsiBuilder.Marker impMarker = declMarker.precede();
      ImportStatement.parseAfterModifiers(builder);
      declMarker.done(GroovyElementTypes.IMPORT_STATEMENT);
      impMarker.error(GroovyBundle.message("import.not.allowed"));
      return true;
    }

    if (isTypeDefinitionStart(builder)) {
      final IElementType tdType = TypeDefinition.parseAfterModifiers(builder, this);
      if (tdType != GroovyElementTypes.WRONGWAY) {
        declMarker.done(tdType);
      }
      else {
        builder.error(GroovyBundle.message("identifier.expected"));
        declMarker.drop();
      }
      return true;
    }

    if (isInClass && parseInitializer(builder)) {
      declMarker.done(GroovyElementTypes.CLASS_INITIALIZER);
      return true;
    }

    final IElementType declType =
      Declaration.parseAfterModifiers(builder, isInClass, isInAnnotation, typeDefinitionName, this, modifiersParsed);
    if (declType != GroovyElementTypes.WRONGWAY) {
      if (declType != null) {
        declMarker.done(declType);
      }
      else {
        declMarker.drop();
      }
      return true;
    }

    if (modifiersParsed) {
      declMarker.drop();
      builder.error(GroovyBundle.message("identifier.expected"));
      return true;
    }

    declMarker.rollbackTo();
    return false;
  }

  private boolean parseInitializer(PsiBuilder builder) {
    ParserUtils.getToken(builder, GroovyTokenTypes.mNLS);
    return GroovyTokenTypes.mLCURLY == builder.getTokenType() && OpenOrClosableBlock.parseOpenBlock(builder, this);
  }

  private static boolean isTypeDefinitionStart(PsiBuilder builder) {
    return GroovyTokenTypes.kCLASS == builder.getTokenType() ||               //class
           GroovyTokenTypes.kINTERFACE == builder.getTokenType() ||           //interface
           GroovyTokenTypes.kENUM == builder.getTokenType() ||                //enum
           GroovyTokenTypes.kTRAIT == builder.getTokenType() && !PathExpression.isQualificationDotAhead(builder) ||               //trait
           ParserUtils.lookAhead(builder, GroovyTokenTypes.mAT, GroovyTokenTypes.kINTERFACE);  //@interface
  }

  public boolean parseStatementWithImports(PsiBuilder builder) {
    if (ImportStatement.parse(builder, this)) {
      return true;
    }
    else {
      return parseStatement(builder, false);
    }
  }

  private boolean parseLabeledStatement(PsiBuilder builder) {

    PsiBuilder.Marker marker = builder.mark();

    if (!ParserUtils.getToken(builder, GroovyTokenTypes.mIDENT) || !ParserUtils.getToken(builder, GroovyTokenTypes.mCOLON)) {
      marker.rollbackTo();
      return false;
    }

    final PsiBuilder.Marker nlsMarker = builder.mark();

    ParserUtils.getToken(builder, GroovyTokenTypes.mNLS);
    if (parseStatement(builder, true)) {
      nlsMarker.drop();
    }
    else {
      nlsMarker.rollbackTo();
      builder.error(GroovyBundle.message("statement.expected"));
    }

    marker.done(GroovyElementTypes.LABELED_STATEMENT);
    return true;
  }
}
