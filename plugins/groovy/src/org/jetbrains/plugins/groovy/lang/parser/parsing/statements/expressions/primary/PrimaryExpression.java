package org.jetbrains.plugins.groovy.lang.parser.parsing.statements.expressions.primary;

import org.jetbrains.plugins.groovy.lang.parser.parsing.util.ParserUtils;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyElementType;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;
import com.intellij.lang.PsiBuilder;

/**
 * @author Ilya.Sergey
 */
public class PrimaryExpression implements GroovyElementTypes {

  public static GroovyElementType parse(PsiBuilder builder){

    if (ParserUtils.getToken(builder, TokenSets.BUILT_IN_TYPE)) return PRIMARY_EXPRESSION;
    if (ParserUtils.getToken(builder, kTHIS)) return PRIMARY_EXPRESSION;
    if (ParserUtils.getToken(builder, kSUPER)) return PRIMARY_EXPRESSION;

    if (mIDENT.equals(builder.getTokenType())){
      ParserUtils.eatElement(builder, REFERENCE_EXPRESSION);
      return PRIMARY_EXPRESSION;
    }

    if (mGSTRING_SINGLE_BEGIN.equals(builder.getTokenType())){
      StringConstructorExpression.parse(builder);
      return PRIMARY_EXPRESSION;
    }

    if (mLBRACK.equals(builder.getTokenType())){
      ListOrMapConstructorExpression.parse(builder);
      return PRIMARY_EXPRESSION;
    }

    if (TokenSets.CONSTANTS.contains(builder.getTokenType())){
      ParserUtils.eatElement(builder, LITERAL);
      return PRIMARY_EXPRESSION;
    }


    // TODO realize me!

    // TODO Add Gstring parsing!

    return WRONGWAY;
  }


}