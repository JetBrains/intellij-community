/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

package org.jetbrains.plugins.groovy.lang.parser;

import com.intellij.lang.ASTNode;
import com.intellij.lang.PsiBuilder;
import com.intellij.lang.PsiParser;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.parser.parsing.auxiliary.Separators;
import org.jetbrains.plugins.groovy.lang.parser.parsing.auxiliary.modifiers.Modifiers;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.*;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.blocks.OpenOrClosableBlock;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.constructor.ConstructorBody;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.declaration.Declaration;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.expressions.AssignmentExpression;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.expressions.ConditionalExpression;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.expressions.StrictContextExpression;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.imports.ImportStatement;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.typeDefinitions.TypeDefinition;
import org.jetbrains.plugins.groovy.lang.parser.parsing.toplevel.CompilationUnit;
import org.jetbrains.plugins.groovy.lang.parser.parsing.util.ParserUtils;

import static org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes.*;

/**
 * Parser for Groovy script files
 *
 * @author ilyas, Dmitry.Krasilschikov
 */
public class GroovyParser implements PsiParser {

  public static final TokenSet RCURLY_ONLY = TokenSet.create(mRCURLY);
  public static final TokenSet CASE_SECTION_END = TokenSet.create(kCASE, kDEFAULT, mRCURLY);

  public boolean parseDeep() {
    return false;
  }

  @NotNull
  public ASTNode parse(IElementType root, PsiBuilder builder) {
    //builder.setDebugMode(true);
    if (root == OPEN_BLOCK) {
      OpenOrClosableBlock.parseOpenBlockDeep(builder, this);
    }
    else if (root == CLOSABLE_BLOCK) {
      OpenOrClosableBlock.parseClosableBlockDeep(builder, this);
    }
    else if (root == CONSTRUCTOR_BODY) {
      ConstructorBody.parseConstructorBodyDeep(builder, this);
    }
    else {
      assert root == GroovyParserDefinition.GROOVY_FILE : root;
      PsiBuilder.Marker rootMarker = builder.mark();
      CompilationUnit.parseFile(builder, this);
      rootMarker.done(root);
    }
    return builder.getTreeBuilt();
  }

  public boolean parseForStatement(PsiBuilder builder) {
    PsiBuilder.Marker marker = builder.mark();

    ParserUtils.getToken(builder, GroovyTokenTypes.kFOR);
    if (!ParserUtils.getToken(builder, GroovyTokenTypes.mLPAREN, GroovyBundle.message("lparen.expected"))) {
      marker.done(FOR_STATEMENT);
      return true;
    }
    if (!ForStatement.forClauseParse(builder, this)) {
      builder.error(GroovyBundle.message("for.clause.expected"));
      marker.done(FOR_STATEMENT);
      return true;
    }

    ParserUtils.getToken(builder, GroovyTokenTypes.mNLS);

    if (!ParserUtils.getToken(builder, GroovyTokenTypes.mRPAREN, GroovyBundle.message("rparen.expected"))) {
      while (ParserUtils.getToken(builder, mNLS)) {}
      marker.done(FOR_STATEMENT);
      return true;
    }

    PsiBuilder.Marker warn = builder.mark();
    ParserUtils.getToken(builder, GroovyTokenTypes.mNLS);

    if (parseExtendedStatement(builder)) {
      warn.rollbackTo();
      marker.done(FOR_STATEMENT);
      return true;
    }

    if (!parseStatement(builder, true)) {
      warn.rollbackTo();
      builder.error(GroovyBundle.message("expression.expected"));
      marker.done(FOR_STATEMENT);
      return true;
    } else {
      warn.drop();
      marker.done(FOR_STATEMENT);
      return true;
    }
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
      ifStmtMarker.done(IF_STATEMENT);
      return true;
    }

    if (!ConditionalExpression.parse(builder, this)) {
      builder.error(GroovyBundle.message("expression.expected"));
    }

    ParserUtils.getToken(builder, GroovyTokenTypes.mNLS);

    if (!ParserUtils.getToken(builder, GroovyTokenTypes.mRPAREN, GroovyBundle.message("rparen.expected"))) {
      while (!builder.eof()) {
        final IElementType type = builder.getTokenType();
        if (GroovyTokenTypes.mNLS  == type || GroovyTokenTypes.mRPAREN == type ||
            GroovyTokenTypes.mLCURLY == type || GroovyTokenTypes.mRCURLY == type) {
          break;
        }

        builder.advanceLexer();
        builder.error(GroovyBundle.message("rparen.expected"));
      }
      if (!ParserUtils.getToken(builder, GroovyTokenTypes.mRPAREN)) {
        ifStmtMarker.done(IF_STATEMENT);
        return true;
      }
    }

    PsiBuilder.Marker warn = builder.mark();
    if (builder.getTokenType() == GroovyTokenTypes.mNLS) {
      ParserUtils.getToken(builder, GroovyTokenTypes.mNLS);
    }

    if (!parseStatement(builder, true) && !parseExtendedStatement(builder)) {
      warn.rollbackTo();
      builder.error(GroovyBundle.message("expression.expected"));
      ifStmtMarker.done(IF_STATEMENT);
      return true;
    } else {
      warn.drop();
    }

    PsiBuilder.Marker rb = builder.mark();
    if (GroovyTokenTypes.kELSE.equals(builder.getTokenType()) ||
        (Separators.parse(builder) &&
            builder.getTokenType() == GroovyTokenTypes.kELSE)) {
      rb.drop();
      ParserUtils.getToken(builder, GroovyTokenTypes.kELSE);

      warn = builder.mark();
      if (builder.getTokenType() == GroovyTokenTypes.mNLS) {
        ParserUtils.getToken(builder, GroovyTokenTypes.mNLS);
      }

      if (!parseStatement(builder, true) && !parseExtendedStatement(builder)) {
        warn.rollbackTo();
        builder.error(GroovyBundle.message("expression.expected"));
        ifStmtMarker.done(IF_STATEMENT);
        return true;
      } else {
        warn.drop();
      }

      ifStmtMarker.done(IF_STATEMENT);
      return true;

    } else {
      rb.rollbackTo();
      ifStmtMarker.done(IF_STATEMENT);
      return true;
    }
  }

  public void parseSwitchCaseList(PsiBuilder builder) {
    if (parseGenericStatement(builder, CASE_SECTION_END)) {
      parseCodeBlock(builder, CASE_SECTION_END);
    }
  }

  //gsp directives, scriptlets and such
  protected boolean isExtendedSeparator(final IElementType tokenType) {
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
      marker.done(WHILE_STATEMENT);
      return true;
    }

    if (!StrictContextExpression.parse(builder, this)) {
      builder.error(GroovyBundle.message("expression.expected"));
    }

    ParserUtils.getToken(builder, GroovyTokenTypes.mNLS);

    if (!ParserUtils.getToken(builder, GroovyTokenTypes.mRPAREN, GroovyBundle.message("rparen.expected"))) {
      marker.done(WHILE_STATEMENT);
      return true;
    }

    PsiBuilder.Marker warn = builder.mark();
    ParserUtils.getToken(builder, GroovyTokenTypes.mNLS);

    if (!parseStatement(builder, true) && !parseExtendedStatement(builder)) {
      warn.rollbackTo();
      builder.error(GroovyBundle.message("expression.expected"));
    } else {
      warn.drop();
    }

    marker.done(WHILE_STATEMENT);
    return true;
  }

  public void parseBlockBody(PsiBuilder builder) {
    skipSeparators(builder);
    parseBlockBodyWithoutSkippingSeparators(builder);
  }

  public void parseBlockBodyWithoutSkippingSeparators(PsiBuilder builder) {
    parseCodeBlock(builder, RCURLY_ONLY);
    ParserUtils.getToken(builder, mNLS);
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
    } else {
      builder.error(GroovyBundle.message("wrong.statement"));
      assert builder.getTokenType() != mLCURLY && builder.getTokenType() != mRCURLY;
      builder.advanceLexer();
    }
    return true;
  }

  private boolean parseSeparatorsWithoutLastNls(PsiBuilder builder, boolean requireSeparator, TokenSet until) {
    boolean hasSeparator = false;
    while (true) {
      while (builder.getTokenType() == mSEMI || isExtendedSeparator(builder.getTokenType())) {
        hasSeparator = true;
        builder.advanceLexer();
      }

      if (builder.getTokenType() == mNLS) {
        PsiBuilder.Marker beforeNls = builder.mark();
        hasSeparator = true;
        builder.advanceLexer();
        if (builder.eof() || until.contains(builder.getTokenType())) {
          beforeNls.rollbackTo();
          return true;
        }
        beforeNls.drop();
      } else {
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
    while (builder.getTokenType() == mSEMI || isExtendedSeparator(builder.getTokenType()) || builder.getTokenType() == mNLS) {
      hasSeparators = true;
      builder.advanceLexer();
    }
    return hasSeparators;
  }

  public boolean parseStatement(PsiBuilder builder, boolean isBlockStatementNeeded) {
    if (isBlockStatementNeeded && GroovyTokenTypes.mLCURLY.equals(builder.getTokenType())) {
      final PsiBuilder.Marker marker = builder.mark();
      OpenOrClosableBlock.parseOpenBlockDeep(builder, this);
      marker.done(BLOCK_STATEMENT);
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
      } else {
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
    if (ParserUtils.lookAhead(builder, GroovyTokenTypes.mIDENT, GroovyTokenTypes.mCOLON)) {
      return parseLabeledStatement(builder);
    }

    //declaration
    PsiBuilder.Marker declMarker = builder.mark();
    boolean modifiersParsed = Modifiers.parse(builder, this);

    if (kIMPORT == builder.getTokenType()) {
      final PsiBuilder.Marker impMarker = declMarker.precede();
      ImportStatement.parseAfterModifiers(builder);
      declMarker.done(IMPORT_STATEMENT);
      impMarker.error(GroovyBundle.message("import.not.allowed"));
      return true;
    }

    if (kCLASS == builder.getTokenType() || kINTERFACE == builder.getTokenType() || kENUM == builder.getTokenType() || mAT == builder.getTokenType()) {
      final IElementType tdType = TypeDefinition.parseAfterModifiers(builder, this);
      if (tdType != WRONGWAY) {
        declMarker.done(tdType);
        return true;
      }
    }

    final IElementType declType = Declaration.parseAfterModifiers(builder, false, false, this, declMarker, modifiersParsed);
    if (declType != WRONGWAY) {
      if (declType != null) {
        declMarker.done(declType);
      } else {
        declMarker.drop();
      }
      return true;
    }

    if (modifiersParsed) {
      declMarker.done(VARIABLE_DEFINITION_ERROR);
      return true;
    }
    declMarker.rollbackTo();

    return AssignmentExpression.parse(builder, this, true);

  }

  public boolean parseStatementWithImports(PsiBuilder builder) {
    if (ImportStatement.parse(builder, this)) {
      return true;
    } else {
      return parseStatement(builder, false);
    }
  }

  private boolean parseLabeledStatement(PsiBuilder builder) {

    PsiBuilder.Marker marker = builder.mark();
    ParserUtils.eatElement(builder, LABEL);
    ParserUtils.getToken(builder, GroovyTokenTypes.mCOLON);

    ParserUtils.getToken(builder, GroovyTokenTypes.mNLS);

    parseStatement(builder, true);

    marker.done(LABELED_STATEMENT);
    return true;
  }
}
