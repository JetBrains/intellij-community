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

  public static void parseArgumentList(PsiBuilder builder, IElementType closingBrace, GroovyParser parser) {
    boolean hasFirstArg = argumentParse(builder, closingBrace, parser);
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
    while (!builder.eof() && !closingBrace.equals(builder.getTokenType())) {
      if (!hasFirstArg) {
        ParserUtils.getToken(builder, mCOMMA);
      } else {
        ParserUtils.getToken(builder, mCOMMA, "',' or '" + closingBrace + "' expected");
      }
      ParserUtils.getToken(builder, mNLS);
      if (!argumentParse(builder, closingBrace, parser)) {
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
    }

    ParserUtils.getToken(builder, mNLS);
  }

  /**
   * Parses argument, possible with label
   *
   * @param builder
   * @return
   */
  private static boolean argumentParse(PsiBuilder builder, IElementType closingBrace, GroovyParser parser) {

    PsiBuilder.Marker argMarker = builder.mark();
    boolean labeled = argumentLabelStartCheck(builder, parser);
    boolean expanded = ParserUtils.getToken(builder, mSTAR);
    if (labeled) {
      ParserUtils.getToken(builder, mCOLON, GroovyBundle.message("colon.expected"));
    }

    // If expression is wrong...
    boolean exprParsed = AssignmentExpression.parse(builder, parser);
    if (labeled && !exprParsed) {
      builder.error(GroovyBundle.message("expression.expected"));
    }
    while (!builder.eof() && labeled &&
            !mCOMMA.equals(builder.getTokenType()) &&
            !closingBrace.equals(builder.getTokenType())) {
      builder.error(GroovyBundle.message("expression.expected"));
      builder.advanceLexer();
      if (AssignmentExpression.parse(builder, parser)) break;
    }

    if (labeled || expanded) {
      argMarker.done(ARGUMENT);
    } else {
      argMarker.drop();
    }

    return labeled || exprParsed;
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
    else if (ParserUtils.lookAhead(builder, mIDENT, mCOLON) ||
             TokenSets.KEYWORD_REFERENCE_NAMES.contains(builder.getTokenType()) ||
             mSTRING_LITERAL.equals(builder.getTokenType()) ||
             mGSTRING_LITERAL.equals(builder.getTokenType())) {
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
    else if (mGSTRING_BEGIN.equals(builder.getTokenType()) ||
             TokenSets.NUMBERS.contains(builder.getTokenType()) ||
             mLBRACK.equals(builder.getTokenType()) ||
             mLPAREN.equals(builder.getTokenType()) ||
             mLCURLY.equals(builder.getTokenType())) {
      PrimaryExpression.parse(builder, parser);
      if (mCOLON.equals(builder.getTokenType())) {
        marker.done(ARGUMENT_LABEL);
        return true;
      }
      else {
        marker.rollbackTo();
        return false;
      }
    }
    else {
      marker.drop();
      return false;
    }

  }

}