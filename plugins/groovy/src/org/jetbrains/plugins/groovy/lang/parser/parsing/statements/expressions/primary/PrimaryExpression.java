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
    if (TokenSets.BUILT_IN_TYPE.contains(tokenType)) {
      ParserUtils.eatElement(builder, BUILT_IN_TYPE_EXPRESSION);
      return BUILT_IN_TYPE_EXPRESSION;
    }
    if (kTHIS == tokenType) {
      ParserUtils.eatElement(builder, THIS_REFERENCE_EXPRESSION);
      return THIS_REFERENCE_EXPRESSION;
    }
    if (kSUPER == tokenType) {
      ParserUtils.eatElement(builder, SUPER_REFERENCE_EXPRESSION);
      return SUPER_REFERENCE_EXPRESSION;
    }
    if (kNEW == tokenType) {
      return newExprParse(builder, parser);
    }
    if (mIDENT == tokenType) {
      ParserUtils.eatElement(builder, REFERENCE_EXPRESSION);
      return REFERENCE_EXPRESSION;
    }
    if (mGSTRING_BEGIN == tokenType) {
      return StringConstructorExpression.parse(builder, parser);
    }
    if (mREGEX_BEGIN == tokenType) {
      return RegexConstructorExpression.parse(builder, parser);
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
    if (tokenType == mSTRING_LITERAL ||
        tokenType == mGSTRING_LITERAL ||
        tokenType == mREGEX_LITERAL) {
      return ParserUtils.eatElement(builder, literalsAsRefExprs ? REFERENCE_EXPRESSION : LITERAL);
    }
    if (TokenSets.CONSTANTS.contains(tokenType)) {
      return ParserUtils.eatElement(builder, LITERAL);
    }
    if (mWRONG_REGEX_LITERAL == tokenType) {
      PsiBuilder.Marker marker = builder.mark();
      builder.advanceLexer();
      builder.error(GroovyBundle.message("wrong.string"));
      marker.done(LITERAL);
      return LITERAL;
    }

    // TODO implement all cases!

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

  /**
   * Parses 'new' expression
   *
   * @param builder
   * @return
   */
  public static GroovyElementType newExprParse(PsiBuilder builder, GroovyParser parser) {
    PsiBuilder.Marker marker = builder.mark();
    final GroovyElementType type = newExprParse(builder, parser, marker);
    marker.done(type);
    return type;
  }

  public static GroovyElementType newExprParse(PsiBuilder builder, GroovyParser parser, PsiBuilder.Marker marker) {
    ParserUtils.getToken(builder, kNEW);
    ParserUtils.getToken(builder, mNLS);
    PsiBuilder.Marker rb = builder.mark();
    TypeArguments.parse(builder);
    if (!TokenSets.BUILT_IN_TYPE.contains(builder.getTokenType()) && mIDENT != builder.getTokenType()) {
      rb.rollbackTo();
    }
    else {
      rb.drop();
    }

    PsiBuilder.Marker anonymousMarker = builder.mark();
    String name = null;
    if (TokenSets.BUILT_IN_TYPE.contains(builder.getTokenType())) {
      ParserUtils.eatElement(builder, BUILT_IN_TYPE);
    }
    else if (mIDENT == builder.getTokenType()) {
      name = builder.getTokenText();
      ReferenceElement.parse(builder, false, true, false, true);
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
        TypeDefinition.parseClassBody(builder, name, parser);
        anonymousMarker.done(ANONYMOUS_CLASS_DEFINITION);
        return NEW_EXPRESSION;
      }
    }
    else if (builder.getTokenType() == mLBRACK) {
      PsiBuilder.Marker forArray = builder.mark();
      while (ParserUtils.getToken(builder, mLBRACK)) {
        ParserUtils.getToken(builder, mNLS);
        if (!AssignmentExpression.parse(builder, parser)) {
          builder.error(GroovyBundle.message("expression.expected"));
        }
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