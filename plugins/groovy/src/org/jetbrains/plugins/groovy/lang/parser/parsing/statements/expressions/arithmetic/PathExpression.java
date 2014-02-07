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

package org.jetbrains.plugins.groovy.lang.parser.parsing.statements.expressions.arithmetic;

import com.intellij.lang.PsiBuilder;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyElementType;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.parser.GroovyParser;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.blocks.OpenOrClosableBlock;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.expressions.arguments.ArgumentList;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.expressions.primary.CompoundStringExpression;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.expressions.primary.PrimaryExpression;
import org.jetbrains.plugins.groovy.lang.parser.parsing.types.TypeArguments;
import org.jetbrains.plugins.groovy.lang.parser.parsing.util.ParserUtils;

import static org.jetbrains.plugins.groovy.lang.parser.parsing.statements.expressions.arithmetic.PathExpression.Result.*;

/**
 * @author ilyas
 */
public class PathExpression implements GroovyElementTypes {

  public enum Result {INVOKED_EXPR, METHOD_CALL, WRONG_WAY, LITERAL}

  private static final TokenSet DOTS = TokenSet.create(mSPREAD_DOT, mOPTIONAL_DOT, mMEMBER_POINTER, mDOT);
  private static final TokenSet PATH_ELEMENT_START = TokenSet.create(mSPREAD_DOT, mOPTIONAL_DOT, mMEMBER_POINTER, mLBRACK, mLPAREN, mLCURLY, mDOT);

  public static boolean parse(@NotNull PsiBuilder builder, @NotNull GroovyParser parser) {
    return parsePathExprQualifierForExprStatement(builder, parser) != WRONG_WAY;
  }

  /**
   * parses method calls with parentheses, property index access, etc
   */
  @NotNull
  public static Result parsePathExprQualifierForExprStatement(@NotNull PsiBuilder builder, @NotNull GroovyParser parser) {
    PsiBuilder.Marker marker = builder.mark();
    IElementType qualifierType = PrimaryExpression.parsePrimaryExpression(builder, parser);
    if (qualifierType != WRONGWAY) {
      return parseAfterQualifier(builder, parser, marker, qualifierType);
    }
    else {
      marker.drop();
      return WRONG_WAY;
    }
  }

  @NotNull
  private static Result parseAfterQualifier(@NotNull PsiBuilder builder,
                                            @NotNull GroovyParser parser,
                                            @NotNull PsiBuilder.Marker marker,
                                            @NotNull IElementType qualifierType) {
    if (isPathElementStart(builder)) {
      if (isLParenthOrLCurlyAfterLiteral(builder, qualifierType)) {
        marker.rollbackTo();
        PsiBuilder.Marker newMarker = builder.mark();
        IElementType newQualifierType = PrimaryExpression.parsePrimaryExpression(builder, parser, true);
        assert newQualifierType != WRONGWAY;
        return parseAfterReference(builder, parser, newMarker);
      }
      else {
        return parseAfterReference(builder, parser, marker);
      }
    }
    else {
      marker.drop();
      if (qualifierType == LITERAL) return Result.LITERAL;
      return INVOKED_EXPR;
    }
  }

  private static boolean isLParenthOrLCurlyAfterLiteral(@NotNull PsiBuilder builder, @NotNull IElementType qualifierType) {
    return qualifierType == LITERAL && (checkForLParenth(builder) || checkForLCurly(builder));
  }

  @NotNull
  private static Result pathElementParse(@NotNull PsiBuilder builder,
                                         @NotNull PsiBuilder.Marker marker,
                                         @NotNull GroovyParser parser,
                                         @NotNull Result result) {


    // Property reference
    if (DOTS.contains(builder.getTokenType()) || ParserUtils.lookAhead(builder, mNLS, mDOT)) {
      if (ParserUtils.lookAhead(builder, mNLS, mDOT)) {
        ParserUtils.getToken(builder, mNLS);
      }
      ParserUtils.getToken(builder, DOTS);
      ParserUtils.getToken(builder, mNLS);
      TypeArguments.parseTypeArguments(builder, true);
      GroovyElementType res = namePartParse(builder, parser);
      if (res != WRONGWAY) {
        PsiBuilder.Marker newMarker = marker.precede();
        marker.done(res);
        return parseAfterReference(builder, parser, newMarker);
      }
      else {
        builder.error(GroovyBundle.message("path.selector.expected"));
        marker.drop();
        return result;
      }
    }
    else if (checkForLParenth(builder)) {
      PrimaryExpression.methodCallArgsParse(builder, parser);
      return parseAfterArguments(builder, marker, parser);
    }
    else if (checkForLCurly(builder)) {
      ParserUtils.getToken(builder, mNLS);
      appendedBlockParse(builder, parser);
      return parseAfterArguments(builder, marker, parser);
    }
    else if (checkForArrayAccess(builder)) {
      indexPropertyArgsParse(builder, parser);
      PsiBuilder.Marker newMarker = marker.precede();
      marker.done(PATH_INDEX_PROPERTY);
      return parseAfterReference(builder, parser, newMarker);
    }
    else {
      marker.drop();
      return result;
    }
  }

  @NotNull
  private static Result parseAfterReference(@NotNull PsiBuilder builder, @NotNull GroovyParser parser, @NotNull PsiBuilder.Marker newMarker) {
    if (checkForLCurly(builder)) {
      PsiBuilder.Marker argsMarker = builder.mark();
      argsMarker.done(ARGUMENTS);
      ParserUtils.getToken(builder, mNLS);
      return pathElementParse(builder, newMarker, parser, METHOD_CALL);
    }
    else {
      return pathElementParse(builder, newMarker, parser, INVOKED_EXPR);
    }
  }

  @NotNull
  private static Result parseAfterArguments(@NotNull PsiBuilder builder, @NotNull PsiBuilder.Marker marker, @NotNull GroovyParser parser) {
    if (checkForLCurly(builder)) {
      ParserUtils.getToken(builder, mNLS);
      return pathElementParse(builder, marker, parser, METHOD_CALL);
    }
    else {
      PsiBuilder.Marker newMarker = marker.precede();
      marker.done(PATH_METHOD_CALL);
      return pathElementParse(builder, newMarker, parser, METHOD_CALL);
    }
  }

  private static boolean checkForLCurly(@NotNull PsiBuilder builder) {
    return ParserUtils.lookAhead(builder, mLCURLY) || ParserUtils.lookAhead(builder, mNLS, mLCURLY);
  }

  private static boolean checkForLParenth(@NotNull PsiBuilder builder) {
    return builder.getTokenType() == mLPAREN;
  }

  public static boolean checkForArrayAccess(@NotNull PsiBuilder builder) {
    return builder.getTokenType() == mLBRACK &&
           !ParserUtils.lookAhead(builder, mLBRACK, mCOLON) &&
           !ParserUtils.lookAhead(builder, mLBRACK, mNLS, mCOLON);
  }

  /**
   * Property selector parsing
   *
   * @param builder
   * @return
   */
  @NotNull
  public static GroovyElementType namePartParse(@NotNull PsiBuilder builder, @NotNull GroovyParser parser) {
    ParserUtils.getToken(builder, mAT);
    if (ParserUtils.getToken(builder, mIDENT) ||
        ParserUtils.getToken(builder, mSTRING_LITERAL) ||
        ParserUtils.getToken(builder, mGSTRING_LITERAL)) {
      return REFERENCE_EXPRESSION;
    }

    final IElementType tokenType = builder.getTokenType();
    if (tokenType == mGSTRING_BEGIN) {
      final boolean result = CompoundStringExpression.parse(builder, parser, true, mGSTRING_BEGIN, mGSTRING_CONTENT, mGSTRING_END, null,
                                                            GSTRING, GroovyBundle.message("string.end.expected"));
      return result ? PATH_PROPERTY_REFERENCE : REFERENCE_EXPRESSION;
    }
    if (tokenType == mREGEX_BEGIN) {
      final boolean result = CompoundStringExpression.parse(builder, parser, true,
                                                            mREGEX_BEGIN, mREGEX_CONTENT, mREGEX_END, mREGEX_LITERAL,
                                                            REGEX, GroovyBundle.message("regex.end.expected"));
      return result ? PATH_PROPERTY_REFERENCE : REFERENCE_EXPRESSION;
    }
    if (tokenType == mDOLLAR_SLASH_REGEX_BEGIN) {
      final boolean result = CompoundStringExpression.parse(builder, parser, true,
                                                            mDOLLAR_SLASH_REGEX_BEGIN, mDOLLAR_SLASH_REGEX_CONTENT, mDOLLAR_SLASH_REGEX_END,
                                                            mDOLLAR_SLASH_REGEX_LITERAL,
                                                            REGEX, GroovyBundle.message("dollar.slash.end.expected"));
      return result ? PATH_PROPERTY_REFERENCE : REFERENCE_EXPRESSION;
    }
    if (tokenType == mLCURLY) {
      OpenOrClosableBlock.parseOpenBlock(builder, parser);
      return PATH_PROPERTY_REFERENCE;
    }
    if (tokenType == mLPAREN) {
      PrimaryExpression.parenthesizedExprParse(builder, parser);
      return PATH_PROPERTY_REFERENCE;
    }
    if (TokenSets.KEYWORDS.contains(builder.getTokenType())) {
      builder.advanceLexer();
      return REFERENCE_EXPRESSION;
    }
    return WRONGWAY;
  }

  /**
   * Method call parsing
   *
   * @param builder
   * @return
   */
  @NotNull
  public static GroovyElementType indexPropertyArgsParse(@NotNull PsiBuilder builder, @NotNull GroovyParser parser) {
    assert builder.getTokenType() == mLBRACK;

    PsiBuilder.Marker marker = builder.mark();
    ParserUtils.getToken(builder, mLBRACK);
    ParserUtils.getToken(builder, mNLS);
    ArgumentList.parseArgumentList(builder, mRBRACK, parser);
    ParserUtils.getToken(builder, mNLS);
    ParserUtils.getToken(builder, mRBRACK, GroovyBundle.message("rbrack.expected"));
    marker.done(ARGUMENTS);
    return PATH_INDEX_PROPERTY;
  }

  /**
   * Appended all argument parsing
   *
   * @param builder
   * @return
   */
  @NotNull
  private static IElementType appendedBlockParse(@NotNull PsiBuilder builder, @NotNull GroovyParser parser) {
    return OpenOrClosableBlock.parseClosableBlock(builder, parser);
  }


  /**
   * Checks for path element start
   *
   * @param builder
   * @return
   */
  private static boolean isPathElementStart(@NotNull PsiBuilder builder) {
    return (PATH_ELEMENT_START.contains(builder.getTokenType()) ||
            ParserUtils.lookAhead(builder, mNLS, mDOT) ||
            ParserUtils.lookAhead(builder, mNLS, mLCURLY));
  }

}
