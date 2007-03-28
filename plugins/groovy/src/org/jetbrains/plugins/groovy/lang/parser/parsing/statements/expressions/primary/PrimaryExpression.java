package org.jetbrains.plugins.groovy.lang.parser.parsing.statements.expressions.primary;

import org.jetbrains.plugins.groovy.lang.parser.parsing.util.ParserUtils;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.blocks.OpenOrClosableBlock;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.expressions.StrictContextExpression;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyElementType;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;
import org.jetbrains.plugins.groovy.GroovyBundle;
import com.intellij.lang.PsiBuilder;

/**
 * @author Ilya.Sergey
 */
public class PrimaryExpression implements GroovyElementTypes {

  public static GroovyElementType parse(PsiBuilder builder) {

    if (TokenSets.BUILT_IN_TYPE.contains(builder.getTokenType())) {
      ParserUtils.eatElement(builder, BUILT_IN_TYPE);
      return PRIMARY_EXPRESSION;
    }
    if (kTHIS.equals(builder.getTokenType())) {
      ParserUtils.eatElement(builder, REFERENCE_EXPRESSION);
      return PRIMARY_EXPRESSION;
    }
    if (kSUPER.equals(builder.getTokenType())) {
      ParserUtils.eatElement(builder, REFERENCE_EXPRESSION);
      return PRIMARY_EXPRESSION;
    }
    if (mIDENT.equals(builder.getTokenType())) {
      ParserUtils.eatElement(builder, REFERENCE_EXPRESSION);
      return PRIMARY_EXPRESSION;
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
    StrictContextExpression.parse(builder);
    if (!ParserUtils.getToken(builder, mRPAREN, GroovyBundle.message("rparen.expected"))) {
      builder.error(GroovyBundle.message("rparen.expected"));
      while (!builder.eof() && !mNLS.equals(builder.getTokenType()) && !mSEMI.equals(builder.getTokenType())
              && !mRPAREN.equals(builder.getTokenType())) {
        builder.error(GroovyBundle.message("rparen.expected"));
        builder.advanceLexer();
      }
      ParserUtils.getToken(builder, mRPAREN);
    }
    marker.done(PARENTHSIZED_EXPRESSION);
    return PARENTHSIZED_EXPRESSION;
  }


}