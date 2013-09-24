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

package org.jetbrains.plugins.groovy.lang.parser.parsing.statements.expressions.primary;

import com.intellij.lang.PsiBuilder;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyElementType;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.parser.GroovyParser;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.blocks.OpenOrClosableBlock;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.expressions.AssignmentExpression;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.expressions.arguments.ArgumentList;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.typeDefinitions.ReferenceElement;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.typeDefinitions.TypeDefinition;
import org.jetbrains.plugins.groovy.lang.parser.parsing.types.TypeArguments;
import org.jetbrains.plugins.groovy.lang.parser.parsing.util.ParserUtils;

/**
 * @author ilyas
 */
public class PrimaryExpression implements GroovyElementTypes {


  public static IElementType parsePrimaryExpression(PsiBuilder builder, GroovyParser parser) {
    return parsePrimaryExpression(builder, parser, false);
  }
  public static IElementType parsePrimaryExpression(PsiBuilder builder, GroovyParser parser, boolean literalsAsRefExprs) {

    final IElementType tokenType = builder.getTokenType();
    if (TokenSets.BUILT_IN_TYPES.contains(tokenType)) {
      ParserUtils.eatElement(builder, BUILT_IN_TYPE_EXPRESSION);
      return BUILT_IN_TYPE_EXPRESSION;
    }
    if (kNEW == tokenType) {
      PsiBuilder.Marker marker = builder.mark();
      final GroovyElementType type = newExprParse(builder, parser);
      marker.done(type);
      return type;
    }
    if (mIDENT == tokenType || kSUPER == tokenType || kTHIS == tokenType) {
      ParserUtils.eatElement(builder, REFERENCE_EXPRESSION);
      return REFERENCE_EXPRESSION;
    }
    if (mGSTRING_BEGIN == tokenType) {
      final boolean result = CompoundStringExpression.parse(builder, parser, false, mGSTRING_BEGIN, mGSTRING_CONTENT, mGSTRING_END, null,
                                                            GSTRING, GroovyBundle.message("string.end.expected"));
      return result ? GSTRING : LITERAL;
    }
    if (mREGEX_BEGIN == tokenType) {
      CompoundStringExpression.parse(builder, parser, false, mREGEX_BEGIN, mREGEX_CONTENT, mREGEX_END, mREGEX_LITERAL,
                                     REGEX, GroovyBundle.message("regex.end.expected"));
      return REGEX;
    }
    if (mDOLLAR_SLASH_REGEX_BEGIN == tokenType) {
      CompoundStringExpression
        .parse(builder, parser, false, mDOLLAR_SLASH_REGEX_BEGIN, mDOLLAR_SLASH_REGEX_CONTENT, mDOLLAR_SLASH_REGEX_END,
               mDOLLAR_SLASH_REGEX_LITERAL,
               REGEX, GroovyBundle.message("dollar.slash.end.expected"));
      return REGEX;
    }
    if (mLBRACK == tokenType) {
      return ListOrMapConstructorExpression.parse(builder, parser);
    }
    if (mLPAREN == tokenType) {
      return parenthesizedExprParse(builder, parser);
    }
    if (mLCURLY == tokenType) {
      return OpenOrClosableBlock.parseClosableBlock(builder, parser);
    }
    if (tokenType == mSTRING_LITERAL || tokenType == mGSTRING_LITERAL) {
      return ParserUtils.eatElement(builder, literalsAsRefExprs ? REFERENCE_EXPRESSION : LITERAL);
    }
    if (TokenSets.CONSTANTS.contains(tokenType)) {
      return ParserUtils.eatElement(builder, LITERAL);
    }

    return WRONGWAY;
  }

  public static GroovyElementType parenthesizedExprParse(PsiBuilder builder, GroovyParser parser) {
    PsiBuilder.Marker marker = builder.mark();
    ParserUtils.getToken(builder, mLPAREN);
    if (!AssignmentExpression.parse(builder, parser)) {
      builder.error(GroovyBundle.message("expression.expected"));
    }
    ParserUtils.getToken(builder, mNLS);
    ParserUtils.getToken(builder, mRPAREN, GroovyBundle.message("rparen.expected"));
    marker.done(PARENTHESIZED_EXPRESSION);
    return PARENTHESIZED_EXPRESSION;
  }

  public static GroovyElementType newExprParse(PsiBuilder builder, GroovyParser parser) {
    ParserUtils.getToken(builder, kNEW);
    ParserUtils.getToken(builder, mNLS);
    PsiBuilder.Marker rb = builder.mark();
    TypeArguments.parseTypeArguments(builder, false);
    if (!TokenSets.BUILT_IN_TYPES.contains(builder.getTokenType()) && mIDENT != builder.getTokenType()) {
      rb.rollbackTo();
    }
    else {
      rb.drop();
    }

    PsiBuilder.Marker anonymousMarker = builder.mark();
    String name = null;
    if (TokenSets.BUILT_IN_TYPES.contains(builder.getTokenType())) {
      ParserUtils.eatElement(builder, BUILT_IN_TYPE);
    }
    else if (TokenSets.CODE_REFERENCE_ELEMENT_NAME_TOKENS.contains(builder.getTokenType())) {
      name = builder.getTokenText();
      ReferenceElement.parse(builder, false, true, false, true, true);
    }
    else {
      builder.error(GroovyBundle.message("type.specification.expected"));
      anonymousMarker.drop();
      return NEW_EXPRESSION;
    }

    if (builder.getTokenType() == mLPAREN || ParserUtils.lookAhead(builder, mNLS, mLPAREN)) {
      ParserUtils.getToken(builder, mNLS);
      methodCallArgsParse(builder, parser);
      if (builder.getTokenType() == mLCURLY || ParserUtils.lookAhead(builder, mNLS, mLCURLY)) {
        ParserUtils.getToken(builder, mNLS);
        TypeDefinition.parseBody(builder, name, parser, false);
        anonymousMarker.done(ANONYMOUS_CLASS_DEFINITION);
        return NEW_EXPRESSION;
      }
    }
    else if (builder.getTokenType() == mLBRACK) {
      PsiBuilder.Marker forArray = builder.mark();
      ParserUtils.getToken(builder, mNLS);
      ParserUtils.getToken(builder, mLBRACK);
      if (!AssignmentExpression.parse(builder, parser)) {
        builder.error(GroovyBundle.message("expression.expected"));
      }
      ParserUtils.getToken(builder, mNLS);
      ParserUtils.getToken(builder, mRBRACK, GroovyBundle.message("rbrack.expected"));
      while (ParserUtils.getToken(builder, mLBRACK)) {
        ParserUtils.getToken(builder, mNLS);
        AssignmentExpression.parse(builder, parser);
        ParserUtils.getToken(builder, mNLS);
        ParserUtils.getToken(builder, mRBRACK, GroovyBundle.message("rbrack.expected"));
      }
      forArray.done(ARRAY_DECLARATOR);
    }
    else {
      builder.error(GroovyBundle.message("lparen.expected"));
    }

    anonymousMarker.drop();
    return NEW_EXPRESSION;
  }

  /**
   * Parses method arguments
   *
   * @param builder
   * @return
   */
  public static boolean methodCallArgsParse(PsiBuilder builder, GroovyParser parser) {
    PsiBuilder.Marker marker = builder.mark();
    if (ParserUtils.getToken(builder, mLPAREN, GroovyBundle.message("lparen.expected"))) {
      ParserUtils.getToken(builder, mNLS);
      ArgumentList.parseArgumentList(builder, mRPAREN, parser);
      ParserUtils.getToken(builder, mNLS);
      ParserUtils.getToken(builder, mRPAREN, GroovyBundle.message("rparen.expected"));
    }

    marker.done(ARGUMENTS);
    return true;
  }
}
