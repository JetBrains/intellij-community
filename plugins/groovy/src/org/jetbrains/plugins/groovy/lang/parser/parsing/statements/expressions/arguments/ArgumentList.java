/*
 * Copyright 2000-2007 JetBrains s.r.o.
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
import org.jetbrains.plugins.groovy.lang.lexer.GroovyElementType;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.expressions.AssignmentExpression;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.expressions.primary.PrimaryExpression;
import org.jetbrains.plugins.groovy.lang.parser.parsing.util.ParserUtils;

/**
 * @author ilyas
 */
public class ArgumentList implements GroovyElementTypes {

  public static void parse(PsiBuilder builder, IElementType closingBrace) {
    GroovyElementType result = argumentParse(builder, closingBrace);
    if (result.equals(WRONGWAY)) {
      if (!closingBrace.equals(builder.getTokenType())) {
        builder.error(GroovyBundle.message("expression.expected"));
      }
      if (!mCOMMA.equals(builder.getTokenType()) &&
              !closingBrace.equals(builder.getTokenType())) {
        builder.advanceLexer();
      }
    }

    ParserUtils.getToken(builder, mNLS);
    while (!builder.eof() && !closingBrace.equals(builder.getTokenType())) {
      ParserUtils.getToken(builder, mCOMMA, GroovyBundle.message("comma.expected"));
      ParserUtils.getToken(builder, mNLS);
      if (argumentParse(builder, closingBrace).equals(WRONGWAY)) {
        if (!closingBrace.equals(builder.getTokenType())) {
          builder.error(GroovyBundle.message("expression.expected"));
        }
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
  private static GroovyElementType argumentParse(PsiBuilder builder, IElementType closingBrace) {

    PsiBuilder.Marker argMarker = builder.mark();
    boolean labeled = argumentLabelStartCheck(builder);
    boolean expanded = ParserUtils.getToken(builder, mSTAR);
    if (labeled) {
      ParserUtils.getToken(builder, mCOLON, GroovyBundle.message("colon.expected"));
    }
    GroovyElementType result = AssignmentExpression.parse(builder);

    // If expression is wrong...
    if (labeled && result.equals(WRONGWAY)) {
      builder.error(GroovyBundle.message("expression.expected"));
    }
    while (!builder.eof() && labeled && result.equals(WRONGWAY) &&
            !mCOMMA.equals(builder.getTokenType()) &&
            !closingBrace.equals(builder.getTokenType())) {
      builder.error(GroovyBundle.message("expression.expected"));
      builder.advanceLexer();
      result = AssignmentExpression.parse(builder);
    }

    if (labeled || expanded) {
      argMarker.done(ARGUMENT);
    } else {
      argMarker.drop();
    }

    if (labeled || !result.equals(WRONGWAY)) {
      return ARGUMENT;
    } else {
      return WRONGWAY;
    }
  }

  /**
   * Checks for argument label. In case when it is so, a caret will not be restored at
   * initial position
   *
   * @param builder
   * @return
   */
  public static boolean argumentLabelStartCheck(PsiBuilder builder) {

    PsiBuilder.Marker marker = builder.mark();
    if (ParserUtils.lookAhead(builder, mSTAR, mCOLON)) {
      builder.advanceLexer();
      marker.done(ARGUMENT_LABEL);
      return true;
    } else if (ParserUtils.lookAhead(builder, mIDENT, mCOLON) ||
            TokenSets.KEYWORD_PROPERTY_NAMES.contains(builder.getTokenType()) ||
            TokenSets.NUMBERS.contains(builder.getTokenType()) ||
            mSTRING_LITERAL.equals(builder.getTokenType()) ||
            mGSTRING_LITERAL.equals(builder.getTokenType())
            ) {
      builder.advanceLexer();
      if (mCOLON.equals(builder.getTokenType())) {
        marker.done(ARGUMENT_LABEL);
        return true;
      } else {
        marker.rollbackTo();
        return false;
      }
    } else if (mGSTRING_SINGLE_BEGIN.equals(builder.getTokenType()) ||
            mLPAREN.equals(builder.getTokenType())) {
      PrimaryExpression.parse(builder);
      if (mCOLON.equals(builder.getTokenType())) {
        marker.done(ARGUMENT_LABEL);
        return true;
      } else {
        marker.rollbackTo();
        return false;
      }
    } else {
      marker.drop();
      return false;
    }

  }

}