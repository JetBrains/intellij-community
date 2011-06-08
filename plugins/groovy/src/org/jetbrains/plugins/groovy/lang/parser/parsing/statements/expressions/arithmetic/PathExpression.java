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

package org.jetbrains.plugins.groovy.lang.parser.parsing.statements.expressions.arithmetic;

import com.intellij.lang.PsiBuilder;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyElementType;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.parser.GroovyParser;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.blocks.OpenOrClosableBlock;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.expressions.arguments.ArgumentList;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.expressions.primary.PrimaryExpression;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.expressions.primary.RegexConstructorExpression;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.expressions.primary.StringConstructorExpression;
import org.jetbrains.plugins.groovy.lang.parser.parsing.types.TypeArguments;
import org.jetbrains.plugins.groovy.lang.parser.parsing.util.ParserUtils;

import static org.jetbrains.plugins.groovy.lang.parser.parsing.statements.expressions.arithmetic.PathExpression.Result.*;

/**
 * @author ilyas
 */
public class PathExpression implements GroovyElementTypes {

  public static boolean parse(PsiBuilder builder, GroovyParser parser) {
    return parsePathExprQualifierForExprStatement(builder, parser) != PathExpression.Result.WRONG_WAY;
  }

  public enum Result {INVOKED_EXPR, METHOD_CALL, WRONG_WAY}

  /**
   * parses method calls with parentheses, property index access, etc
   */
  public static Result parsePathExprQualifierForExprStatement(PsiBuilder builder, GroovyParser parser) {
    PsiBuilder.Marker marker = builder.mark();
    final PsiBuilder.Marker marker1 = builder.mark();
    IElementType qualifierType = PrimaryExpression.parsePrimaryExpression(builder, parser);
    if (qualifierType != WRONGWAY) {
      Result result;
      if (isPathElementStart(builder)) {
        if (builder.getTokenType() == mLPAREN && qualifierType == LITERAL) {
          marker1.rollbackTo();
          qualifierType = PrimaryExpression.parsePrimaryExpression(builder, parser, true);
          assert qualifierType != WRONGWAY;
        }
        else {
          marker1.drop();
        }
        PsiBuilder.Marker newMarker = marker.precede();
        marker.drop();
        if (checkForLCurly(builder)) {
          PsiBuilder.Marker argsMarker = builder.mark();
          argsMarker.done(ARGUMENTS);
          ParserUtils.getToken(builder, mNLS);
          result = pathElementParse(builder, newMarker, parser, qualifierType, METHOD_CALL);
        }
        else {
          result = pathElementParse(builder, newMarker, parser, qualifierType, INVOKED_EXPR);
        }
      }
      else {
        marker1.drop();
        marker.drop();
        result = INVOKED_EXPR;
      }
      return result;
    }
    else {
      marker1.drop();
      marker.drop();
      return WRONG_WAY;
    }
  }

  /**
   * Any path element parsing
   *
   * @param builder
   * @param marker
   * @return
   */

  private static final TokenSet DOTS = TokenSet.create(mSPREAD_DOT, mOPTIONAL_DOT, mMEMBER_POINTER, mDOT);

  private static Result pathElementParse(PsiBuilder builder,
                                                    PsiBuilder.Marker marker,
                                                    GroovyParser parser,
                                                    IElementType qualifierType,
                                                    Result result) {

    GroovyElementType res;

    // Property reference
    if (DOTS.contains(builder.getTokenType()) || ParserUtils.lookAhead(builder, mNLS, mDOT)) {
      if (ParserUtils.lookAhead(builder, mNLS, mDOT)) {
        ParserUtils.getToken(builder, mNLS);
      }
      ParserUtils.getToken(builder, DOTS);
      ParserUtils.getToken(builder, mNLS);
      TypeArguments.parse(builder);
      if (kNEW.equals(builder.getTokenType())) {
        res = PrimaryExpression.newExprParse(builder, parser, marker);
      }
      else if (kTHIS.equals(builder.getTokenType()) || kSUPER.equals(builder.getTokenType())) {
        res = parseThisSuperExpression(builder, qualifierType);
      }
      else {
        res = namePartParse(builder, parser);
      }
      if (!res.equals(WRONGWAY)) {
        PsiBuilder.Marker newMarker = marker.precede();
        marker.done(res);
        if (checkForLCurly(builder)) {
          PsiBuilder.Marker argsMarker = builder.mark();
          argsMarker.done(ARGUMENTS);
          ParserUtils.getToken(builder, mNLS);
          result = pathElementParse(builder, newMarker, parser, res, METHOD_CALL);
        }
        else {
          result = pathElementParse(builder, newMarker, parser, res, INVOKED_EXPR);
        }
      }
      else {
        builder.error(GroovyBundle.message("path.selector.expected"));
        marker.drop();
      }
    }
    else if (mLPAREN.equals(builder.getTokenType())) {
      PrimaryExpression.methodCallArgsParse(builder, parser);
      if (checkForLCurly(builder)) {
        ParserUtils.getToken(builder, mNLS);
        result = pathElementParse(builder, marker, parser, qualifierType, METHOD_CALL);
      }
      else {
        PsiBuilder.Marker newMarker = marker.precede();
        marker.done(PATH_METHOD_CALL);
        result = pathElementParse(builder, newMarker, parser, qualifierType, METHOD_CALL);
      }
    }
    else if (checkForLCurly(builder)) {
      ParserUtils.getToken(builder, mNLS);
      appendedBlockParse(builder, parser);
      if (checkForLCurly(builder)) {
        ParserUtils.getToken(builder, mNLS);
        result = pathElementParse(builder, marker, parser, qualifierType, METHOD_CALL);
      }
      else {
        PsiBuilder.Marker newMarker = marker.precede();
        marker.done(PATH_METHOD_CALL);
        result = pathElementParse(builder, newMarker, parser, PATH_METHOD_CALL, METHOD_CALL);
      }
    }
    else if (checkForArrayAccess(builder)) {
      indexPropertyArgsParse(builder, parser);
      PsiBuilder.Marker newMarker = marker.precede();
      marker.done(PATH_INDEX_PROPERTY);
      if (checkForLCurly(builder)) {
        PsiBuilder.Marker argsMarker = builder.mark();
        argsMarker.done(ARGUMENTS);
        ParserUtils.getToken(builder, mNLS);
        result = pathElementParse(builder, newMarker, parser, PATH_INDEX_PROPERTY, METHOD_CALL);
      }
      else {
        result = pathElementParse(builder, newMarker, parser, PATH_INDEX_PROPERTY, INVOKED_EXPR);
      }
    }
    else {
      marker.drop();
    }
    return result;
  }

  private static boolean checkForLCurly(PsiBuilder builder) {
    return ParserUtils.lookAhead(builder, mLCURLY) || ParserUtils.lookAhead(builder, mNLS, mLCURLY);
  }

  public static boolean checkForArrayAccess(PsiBuilder builder) {
    return mLBRACK.equals(builder.getTokenType()) &&
           !ParserUtils.lookAhead(builder, mLBRACK, mCOLON) &&
           !ParserUtils.lookAhead(builder, mLBRACK, mNLS, mCOLON);
  }

  private static GroovyElementType parseThisSuperExpression(PsiBuilder builder, IElementType qualifierType) {
    if (qualifierType != REFERENCE_EXPRESSION) {
      return WRONGWAY;
    }
    final IElementType tokenType = builder.getTokenType();
    builder.advanceLexer();
    final GroovyElementType type;
    if (kTHIS.equals(tokenType)) {
      type = THIS_REFERENCE_EXPRESSION;
    }
    else {
      type = SUPER_REFERENCE_EXPRESSION;
    }

    return type;
  }

  /**
   * Property selector parsing
   *
   * @param builder
   * @return
   */
  public static GroovyElementType namePartParse(PsiBuilder builder, GroovyParser parser) {
    ParserUtils.getToken(builder, mAT);
    if (ParserUtils.getToken(builder, mIDENT) ||
        ParserUtils.getToken(builder, mSTRING_LITERAL) ||
        ParserUtils.getToken(builder, mGSTRING_LITERAL)) {
      return REFERENCE_EXPRESSION;
    }

    final IElementType tokenType = builder.getTokenType();
    if (mREGEX_LITERAL.equals(tokenType)) {
      ParserUtils.eatElement(builder, REGEX);
      return PATH_PROPERTY_REFERENCE;
    }
    if (mGSTRING_BEGIN.equals(tokenType)) {
      StringConstructorExpression.parse(builder, parser);
      return PATH_PROPERTY_REFERENCE;
    }
    if (mREGEX_BEGIN.equals(tokenType)) {
      RegexConstructorExpression.parse(builder, parser);
      return PATH_PROPERTY_REFERENCE;
    }
    if (mLCURLY.equals(tokenType)) {
      OpenOrClosableBlock.parseOpenBlock(builder, parser);
      return PATH_PROPERTY_REFERENCE;
    }
    if (mLPAREN.equals(tokenType)) {
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
  public static GroovyElementType indexPropertyArgsParse(PsiBuilder builder, GroovyParser parser) {
    assert mLBRACK.equals(builder.getTokenType());

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
  private static IElementType appendedBlockParse(PsiBuilder builder, GroovyParser parser) {
    return OpenOrClosableBlock.parseClosableBlock(builder, parser);
  }


  /**
   * Checks for path element start
   *
   * @param builder
   * @return
   */
  private static boolean isPathElementStart(PsiBuilder builder) {
    return (PATH_ELEMENT_START.contains(builder.getTokenType()) ||
            ParserUtils.lookAhead(builder, mNLS, mDOT) ||
            ParserUtils.lookAhead(builder, mNLS, mLCURLY));
  }

  /**
   * FIRST(1) of PathElement
   */
  private static final TokenSet PATH_ELEMENT_START =
    TokenSet.create(mSPREAD_DOT, mOPTIONAL_DOT, mMEMBER_POINTER, mLBRACK, mLPAREN, mLCURLY, mDOT);


}
