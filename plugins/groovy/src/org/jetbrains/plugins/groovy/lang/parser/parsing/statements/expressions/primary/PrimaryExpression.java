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

package org.jetbrains.plugins.groovy.lang.parser.parsing.statements.expressions.primary;

import com.intellij.lang.PsiBuilder;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyElementType;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.blocks.OpenOrClosableBlock;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.expressions.AssignmentExpression;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.expressions.StrictContextExpression;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.expressions.arguments.ArgumentList;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.typeDefinitions.ReferenceElement;
import org.jetbrains.plugins.groovy.lang.parser.parsing.types.TypeArguments;
import org.jetbrains.plugins.groovy.lang.parser.parsing.util.ParserUtils;

/**
 * @author ilyas
 */
public class PrimaryExpression implements GroovyElementTypes {

  public static GroovyElementType parse(PsiBuilder builder) {

    if (TokenSets.BUILT_IN_TYPE.contains(builder.getTokenType())) {
      ParserUtils.eatElement(builder, BUILT_IN_TYPE);
      return PRIMARY_EXPRESSION;
    }
    if (kTHIS.equals(builder.getTokenType())) {
      ParserUtils.eatElement(builder, THIS_REFERENCE_EXPRESSION);
      return PRIMARY_EXPRESSION;
    }
    if (kSUPER.equals(builder.getTokenType())) {
      ParserUtils.eatElement(builder, SUPER_REFERENCE_EXPRESSION);
      return PRIMARY_EXPRESSION;
    }
    if (kNEW.equals(builder.getTokenType())) {
      newExprParse(builder);
      return PRIMARY_EXPRESSION;
    }
    if (mIDENT.equals(builder.getTokenType())) {
      ParserUtils.eatElement(builder, REFERENCE_EXPRESSION);
      return REFERENCE_EXPRESSION;
    }
    if (mGSTRING_SINGLE_BEGIN.equals(builder.getTokenType())) {
      StringConstructorExpression.parse(builder);
      return PRIMARY_EXPRESSION;
    }
    if (mREGEX_BEGIN.equals(builder.getTokenType())) {
      RegexConstructorExpression.parse(builder);
      return PRIMARY_EXPRESSION;
    }
    if (mLBRACK.equals(builder.getTokenType())) {
      ListOrMapConstructorExpression.parse(builder);
      return PRIMARY_EXPRESSION;
    }
    if (mLPAREN.equals(builder.getTokenType())) {
      return parenthesizedExprParse(builder);
    }
    if (mLCURLY.equals(builder.getTokenType())) {
      OpenOrClosableBlock.parseClosableBlock(builder);
      return PRIMARY_EXPRESSION;
    }
    if (TokenSets.CONSTANTS.contains(builder.getTokenType())) {
      ParserUtils.eatElement(builder, LITERAL);
      return PRIMARY_EXPRESSION;
    }
    if (TokenSets.WRONG_CONSTANTS.contains(builder.getTokenType())) {
      PsiBuilder.Marker marker = builder.mark();
      builder.advanceLexer();
      builder.error(GroovyBundle.message("wrong.string"));
      marker.done(LITERAL);
      return PRIMARY_EXPRESSION;
    }

    // TODO implement all cases!

    return WRONGWAY;
  }

  public static GroovyElementType parenthesizedExprParse(PsiBuilder builder) {
    PsiBuilder.Marker marker = builder.mark();
    ParserUtils.getToken(builder, mLPAREN);
    GroovyElementType innerExprType = StrictContextExpression.parse(builder);
    if (innerExprType == WRONGWAY) {
      marker.rollbackTo();
      return WRONGWAY;
    }
    ParserUtils.getToken(builder, mNLS);
    if (!ParserUtils.getToken(builder, mRPAREN, GroovyBundle.message("rparen.expected"))) {
      builder.error(GroovyBundle.message("rparen.expected"));
      while (!builder.eof() && !mNLS.equals(builder.getTokenType()) && !mSEMI.equals(builder.getTokenType())
              && !mRPAREN.equals(builder.getTokenType())) {
        builder.error(GroovyBundle.message("rparen.expected"));
        builder.advanceLexer();
      }
      ParserUtils.getToken(builder, mRPAREN);
    }
    marker.done(PARENTHESIZED_EXPRESSION);
    return PARENTHESIZED_EXPRESSION;
  }

  /**
   * Parses 'new' expression
   *
   * @param builder
   * @return
   */
  public static GroovyElementType newExprParse(PsiBuilder builder) {
    PsiBuilder.Marker marker = builder.mark();
    ParserUtils.getToken(builder, kNEW);
    ParserUtils.getToken(builder, mNLS);
    PsiBuilder.Marker rb = builder.mark();
    TypeArguments.parse(builder);
    if (!TokenSets.BUILT_IN_TYPE.contains(builder.getTokenType()) &&
            !mIDENT.equals(builder.getTokenType())) {
      rb.rollbackTo();
    } else {
      rb.drop();
    }


    if (TokenSets.BUILT_IN_TYPE.contains(builder.getTokenType())) {
      ParserUtils.eatElement(builder, BUILT_IN_TYPE);
    } else if (mIDENT.equals(builder.getTokenType())) {
      ReferenceElement.parseReferenceElement(builder);
    } else {
      builder.error(GroovyBundle.message("type.specification.expected"));
      marker.done(NEW_EXPRESSION);
      return NEW_EXPRESSION;
    }

    if (builder.getTokenType() == mLPAREN ||
            ParserUtils.lookAhead(builder, mNLS, mLPAREN)) {

      ParserUtils.getToken(builder, mNLS);
      methodCallArgsParse(builder);
      if (builder.getTokenType() == mLCURLY) {
        OpenOrClosableBlock.parseClosableBlock(builder);
      }
    } else if (builder.getTokenType() == mLBRACK) {
      PsiBuilder.Marker forArray = builder.mark();
      while (ParserUtils.getToken(builder, mLBRACK)) {
        ParserUtils.getToken(builder, mNLS);
        AssignmentExpression.parse(builder);
        ParserUtils.getToken(builder, mNLS);
        ParserUtils.getToken(builder, mRBRACK, GroovyBundle.message("rbrack.expected"));
      }
      forArray.done(ARRAY_DECLARATOR);
    } else {
      builder.error(GroovyBundle.message("lparen.expected"));
    }


    marker.done(NEW_EXPRESSION);
    return NEW_EXPRESSION;
  }

  /**
   * Parses method arguments
   *
   * @param builder
   * @return
   */
  private static GroovyElementType methodCallArgsParse(PsiBuilder builder) {
    if (ParserUtils.getToken(builder, mLPAREN, GroovyBundle.message("lparen.expected"))) {
      ParserUtils.getToken(builder, mNLS);
      if (ParserUtils.getToken(builder, mRPAREN)) {
        return PATH_METHOD_CALL;
      }
      ArgumentList.parse(builder, mRPAREN);
      ParserUtils.getToken(builder, mNLS);
      ParserUtils.getToken(builder, mRPAREN, GroovyBundle.message("rparen.expected"));
    }
    return PATH_METHOD_CALL;
  }


}