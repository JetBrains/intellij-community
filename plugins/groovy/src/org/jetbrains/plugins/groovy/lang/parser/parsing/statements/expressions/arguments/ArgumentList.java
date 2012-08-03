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

package org.jetbrains.plugins.groovy.lang.parser.parsing.statements.expressions.arguments;

import com.intellij.lang.PsiBuilder;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.parser.GroovyParser;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.expressions.AssignmentExpression;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.expressions.primary.PrimaryExpression;
import org.jetbrains.plugins.groovy.lang.parser.parsing.util.ParserUtils;

/**
 * @author ilyas
 */
public class ArgumentList implements GroovyElementTypes {
  private static final TokenSet CONTROL_KEYWORDS = TokenSet.create(kASSERT, kBREAK, kCASE, kCLASS,
                                                          kCONTINUE, kDEF, kDEFAULT, kDO, kELSE, kENUM, kFINAL,
                                                          kFOR, kFINALLY, kIF, kIMPLEMENTS, kIMPORT,
                                                          kINTERFACE, kNATIVE, kPACKAGE, kPRIVATE, kPROTECTED, kPUBLIC,
                                                          kRETURN, kSTATIC, kSTRICTFP, kSWITCH, kSYNCHRONIZED,
                                                          kTHROW, kTHROWS, kTRANSIENT, kTRY, kVOLATILE, kWHILE);


  public static void parseArgumentList(PsiBuilder builder, IElementType closingBrace, GroovyParser parser) {
    boolean hasFirstArg = argumentParse(builder, parser);
    if (!hasFirstArg) {
      if (!closingBrace.equals(builder.getTokenType())) {
        builder.error(GroovyBundle.message("expression.expected"));
      }
      if (mRCURLY.equals(builder.getTokenType())) return;

      if (!mCOMMA.equals(builder.getTokenType()) &&
              !closingBrace.equals(builder.getTokenType())) {
        builder.advanceLexer();
      }
    }

    ParserUtils.getToken(builder, mNLS);
    boolean hasErrors = false;
    while (!builder.eof() && !closingBrace.equals(builder.getTokenType())) {
      if (!ParserUtils.getToken(builder, mCOMMA) && hasFirstArg) {
        builder.error("',' or '" + closingBrace + "' expected");
        hasErrors = true;
      }
      ParserUtils.getToken(builder, mNLS);
      if (hasErrors && CONTROL_KEYWORDS.contains(builder.getTokenType())) {
        return;
      }
      if (!argumentParse(builder, parser)) {
        if (!closingBrace.equals(builder.getTokenType())) {
          builder.error(GroovyBundle.message("expression.expected"));
          hasErrors = true;
        }
        if (mRCURLY.equals(builder.getTokenType())) return;

        if (!mCOMMA.equals(builder.getTokenType()) &&
                !closingBrace.equals(builder.getTokenType())) {
          builder.advanceLexer();
        }
      }
      ParserUtils.getToken(builder, mNLS);
    }

    ParserUtils.getToken(builder, mNLS);
  }

  /**
   * Parses argument, possible with label
   *
   * @param builder
   * @return
   */
  private static boolean argumentParse(PsiBuilder builder, GroovyParser parser) {
    PsiBuilder.Marker argMarker = builder.mark();

    if (argumentLabelStartCheck(builder, parser)) {
      ParserUtils.getToken(builder, mCOLON, GroovyBundle.message("colon.expected"));
      if (!AssignmentExpression.parse(builder, parser)) {
        builder.error(GroovyBundle.message("expression.expected"));
      }
      argMarker.done(NAMED_ARGUMENT);
      return true;
    }

    if (ParserUtils.getToken(builder, mSTAR)) {
      if (AssignmentExpression.parse(builder, parser)) {
        argMarker.done(SPREAD_ARGUMENT);
      }
      else {
        builder.error(GroovyBundle.message("colon.expected"));
        argMarker.done(NAMED_ARGUMENT);
      }
      return true;
    }

    argMarker.drop();
    return AssignmentExpression.parse(builder, parser);
  }

  /**
   * Checks for argument label. In case when it is so, a caret will not be restored at
   * initial position
   *
   * @param builder
   * @return
   */
  public static boolean argumentLabelStartCheck(PsiBuilder builder, GroovyParser parser) {
    PsiBuilder.Marker marker = builder.mark();
    if (ParserUtils.lookAhead(builder, mSTAR, mCOLON)) {
      builder.advanceLexer();
      marker.done(ARGUMENT_LABEL);
      return true;
    }

    final IElementType type = builder.getTokenType();
    if (ParserUtils.lookAhead(builder, mIDENT, mCOLON) ||
        TokenSets.KEYWORDS.contains(type) ||
        mSTRING_LITERAL.equals(type) ||
        mGSTRING_LITERAL.equals(type)) {
      builder.advanceLexer();
      if (mCOLON.equals(builder.getTokenType())) {
        marker.done(ARGUMENT_LABEL);
        return true;
      }
      else {
        marker.rollbackTo();
        return false;
      }
    }

    if (mGSTRING_BEGIN.equals(type) ||
        mREGEX_BEGIN.equals(type) ||
        mDOLLAR_SLASH_REGEX_BEGIN.equals(type) ||
        TokenSets.NUMBERS.contains(type) ||
        mLBRACK.equals(type) ||
        mLPAREN.equals(type) ||
        mLCURLY.equals(type)) {
      PrimaryExpression.parsePrimaryExpression(builder, parser);
      if (mCOLON.equals(builder.getTokenType())) {
        marker.done(ARGUMENT_LABEL);
        return true;
      }
      else {
        marker.rollbackTo();
        return false;
      }
    }

    marker.drop();
    return false;
  }
}
