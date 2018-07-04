// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.plugins.groovy.lang.parser.parsing.statements.expressions.primary;

import com.intellij.lang.PsiBuilder;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.parser.GroovyParser;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.blocks.OpenOrClosableBlock;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.expressions.AssignmentExpression;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.expressions.arguments.ArgumentList;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.expressions.arithmetic.PathExpression;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.typeDefinitions.ReferenceElement;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.typeDefinitions.TypeDefinition;
import org.jetbrains.plugins.groovy.lang.parser.parsing.types.TypeArguments;
import org.jetbrains.plugins.groovy.lang.parser.parsing.util.ParserUtils;

/**
 * @author ilyas
 */
public class PrimaryExpression {


  public static IElementType parsePrimaryExpression(PsiBuilder builder, GroovyParser parser) {
    return parsePrimaryExpression(builder, parser, false);
  }
  public static IElementType parsePrimaryExpression(PsiBuilder builder, GroovyParser parser, boolean literalsAsRefExprs) {

    final IElementType tokenType = builder.getTokenType();
    if (TokenSets.BUILT_IN_TYPES.contains(tokenType)) {
      ParserUtils.eatElement(builder, GroovyElementTypes.BUILT_IN_TYPE_EXPRESSION);
      return GroovyElementTypes.BUILT_IN_TYPE_EXPRESSION;
    }
    if (GroovyTokenTypes.kNEW == tokenType) {
      PsiBuilder.Marker marker = builder.mark();
      final IElementType type = newExprParse(builder, parser);
      marker.done(type);
      return type;
    }
    if (GroovyTokenTypes.mIDENT == tokenType || GroovyTokenTypes.kSUPER == tokenType || GroovyTokenTypes.kTHIS == tokenType ||
        TokenSets.CODE_REFERENCE_ELEMENT_NAME_TOKENS.contains(tokenType) && PathExpression.isQualificationDotAhead(builder)) {
      return ParserUtils.eatElement(builder, GroovyElementTypes.REFERENCE_EXPRESSION);
    }
    if (GroovyTokenTypes.mGSTRING_BEGIN == tokenType) {
      final boolean result = CompoundStringExpression.parse(builder, parser, false, GroovyTokenTypes.mGSTRING_BEGIN,
                                                            GroovyTokenTypes.mGSTRING_CONTENT, GroovyTokenTypes.mGSTRING_END, null,
                                                            GroovyElementTypes.GSTRING, GroovyBundle.message("string.end.expected"));
      return result ? GroovyElementTypes.GSTRING : GroovyElementTypes.LITERAL;
    }
    if (GroovyTokenTypes.mREGEX_BEGIN == tokenType) {
      CompoundStringExpression.parse(builder, parser, false, GroovyTokenTypes.mREGEX_BEGIN, GroovyTokenTypes.mREGEX_CONTENT,
                                     GroovyTokenTypes.mREGEX_END, GroovyTokenTypes.mREGEX_LITERAL,
                                     GroovyElementTypes.REGEX, GroovyBundle.message("regex.end.expected"));
      return GroovyElementTypes.REGEX;
    }
    if (GroovyTokenTypes.mDOLLAR_SLASH_REGEX_BEGIN == tokenType) {
      CompoundStringExpression
        .parse(builder, parser, false, GroovyTokenTypes.mDOLLAR_SLASH_REGEX_BEGIN, GroovyTokenTypes.mDOLLAR_SLASH_REGEX_CONTENT,
               GroovyTokenTypes.mDOLLAR_SLASH_REGEX_END,
               GroovyTokenTypes.mDOLLAR_SLASH_REGEX_LITERAL,
               GroovyElementTypes.REGEX, GroovyBundle.message("dollar.slash.end.expected"));
      return GroovyElementTypes.REGEX;
    }
    if (GroovyTokenTypes.mLBRACK == tokenType) {
      return ListOrMapConstructorExpression.parse(builder, parser);
    }
    if (GroovyTokenTypes.mLPAREN == tokenType) {
      return parenthesizedExprParse(builder, parser);
    }
    if (GroovyTokenTypes.mLCURLY == tokenType) {
      return OpenOrClosableBlock.parseClosableBlock(builder, parser);
    }
    if (tokenType == GroovyTokenTypes.mSTRING_LITERAL || tokenType == GroovyTokenTypes.mGSTRING_LITERAL) {
      return ParserUtils.eatElement(builder, literalsAsRefExprs ? GroovyElementTypes.REFERENCE_EXPRESSION : GroovyElementTypes.LITERAL);
    }
    if (TokenSets.CONSTANTS.contains(tokenType)) {
      return ParserUtils.eatElement(builder, GroovyElementTypes.LITERAL);
    }

    return GroovyElementTypes.WRONGWAY;
  }

  public static IElementType parenthesizedExprParse(PsiBuilder builder, GroovyParser parser) {
    PsiBuilder.Marker marker = builder.mark();
    ParserUtils.getToken(builder, GroovyTokenTypes.mLPAREN);
    if (!AssignmentExpression.parse(builder, parser)) {
      builder.error(GroovyBundle.message("expression.expected"));
    }
    ParserUtils.getToken(builder, GroovyTokenTypes.mNLS);
    ParserUtils.getToken(builder, GroovyTokenTypes.mRPAREN, GroovyBundle.message("rparen.expected"));
    marker.done(GroovyElementTypes.PARENTHESIZED_EXPRESSION);
    return GroovyElementTypes.PARENTHESIZED_EXPRESSION;
  }

  public static IElementType newExprParse(PsiBuilder builder, GroovyParser parser) {
    ParserUtils.getToken(builder, GroovyTokenTypes.kNEW);
    ParserUtils.getToken(builder, GroovyTokenTypes.mNLS);
    PsiBuilder.Marker rb = builder.mark();
    TypeArguments.parseTypeArguments(builder, false);
    if (!TokenSets.BUILT_IN_TYPES.contains(builder.getTokenType()) && GroovyTokenTypes.mIDENT != builder.getTokenType()) {
      rb.rollbackTo();
    }
    else {
      rb.drop();
    }

    PsiBuilder.Marker anonymousMarker = builder.mark();
    String name;
    if (TokenSets.BUILT_IN_TYPES.contains(builder.getTokenType())) {
      ParserUtils.eatElement(builder, GroovyElementTypes.BUILT_IN_TYPE);
      name = null;
    }
    else if (TokenSets.CODE_REFERENCE_ELEMENT_NAME_TOKENS.contains(builder.getTokenType())) {
      name = builder.getTokenText();
      ReferenceElement.parse(builder, false, true, false, true, true);
    }
    else {
      builder.error(GroovyBundle.message("type.specification.expected"));
      anonymousMarker.drop();
      return GroovyElementTypes.NEW_EXPRESSION;
    }

    if (builder.getTokenType() == GroovyTokenTypes.mLPAREN || ParserUtils.lookAhead(builder, GroovyTokenTypes.mNLS,
                                                                                    GroovyTokenTypes.mLPAREN)) {
      ParserUtils.getToken(builder, GroovyTokenTypes.mNLS);
      methodCallArgsParse(builder, parser);
      if (builder.getTokenType() == GroovyTokenTypes.mLCURLY || ParserUtils.lookAhead(builder, GroovyTokenTypes.mNLS,
                                                                                      GroovyTokenTypes.mLCURLY)) {
        ParserUtils.getToken(builder, GroovyTokenTypes.mNLS);
        TypeDefinition.parseBody(builder, name, parser, false);
        anonymousMarker.done(GroovyElementTypes.ANONYMOUS_CLASS_DEFINITION);
        return GroovyElementTypes.NEW_EXPRESSION;
      }
    }
    else if (builder.getTokenType() == GroovyTokenTypes.mLBRACK) {
      PsiBuilder.Marker forArray = builder.mark();
      ParserUtils.getToken(builder, GroovyTokenTypes.mNLS);
      ParserUtils.getToken(builder, GroovyTokenTypes.mLBRACK);
      if (!AssignmentExpression.parse(builder, parser)) {
        builder.error(GroovyBundle.message("expression.expected"));
      }
      ParserUtils.getToken(builder, GroovyTokenTypes.mNLS);
      ParserUtils.getToken(builder, GroovyTokenTypes.mRBRACK, GroovyBundle.message("rbrack.expected"));
      while (ParserUtils.getToken(builder, GroovyTokenTypes.mLBRACK)) {
        ParserUtils.getToken(builder, GroovyTokenTypes.mNLS);
        AssignmentExpression.parse(builder, parser);
        ParserUtils.getToken(builder, GroovyTokenTypes.mNLS);
        ParserUtils.getToken(builder, GroovyTokenTypes.mRBRACK, GroovyBundle.message("rbrack.expected"));
      }
      forArray.done(GroovyElementTypes.ARRAY_DECLARATOR);
    }
    else {
      builder.error(GroovyBundle.message("lparen.expected"));
    }

    anonymousMarker.drop();
    return GroovyElementTypes.NEW_EXPRESSION;
  }

  /**
   * Parses method arguments
   *
   * @param builder
   * @return
   */
  public static boolean methodCallArgsParse(PsiBuilder builder, GroovyParser parser) {
    PsiBuilder.Marker marker = builder.mark();
    if (ParserUtils.getToken(builder, GroovyTokenTypes.mLPAREN, GroovyBundle.message("lparen.expected"))) {
      ParserUtils.getToken(builder, GroovyTokenTypes.mNLS);
      ArgumentList.parseArgumentList(builder, GroovyTokenTypes.mRPAREN, parser);
      ParserUtils.getToken(builder, GroovyTokenTypes.mNLS);
      ParserUtils.getToken(builder, GroovyTokenTypes.mRPAREN, GroovyBundle.message("rparen.expected"));
    }

    marker.done(GroovyElementTypes.ARGUMENTS);
    return true;
  }
}
