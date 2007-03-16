package org.jetbrains.plugins.groovy.lang.parser.parsing.statements.auxilary;

import org.jetbrains.plugins.groovy.lang.parser.parsing.Construction;
import org.jetbrains.plugins.groovy.lang.parser.parsing.auxiliary.Pairs;
import org.jetbrains.plugins.groovy.lang.parser.parsing.util.ParserUtils;
import org.jetbrains.plugins.groovy.GroovyBundle;
import com.intellij.psi.tree.IElementType;
import com.intellij.lang.PsiBuilder;

import java.util.HashMap;
import java.util.Map;

/**
 * @autor: Dmitry.Krasilschikov
 * @date: 16.03.2007
 */
public class BalancedBrackets implements Construction {
  public static IElementType parse(PsiBuilder builder) {
    PsiBuilder.Marker bbm = builder.mark();

    IElementType balancedTokens;
    IElementType myBracket = null;

    if (ParserUtils.getToken(builder, mLPAREN)) {
      myBracket = mLPAREN;
    }

    if (ParserUtils.getToken(builder, mLCURLY)) {
      myBracket = mLCURLY;
    }

    if (ParserUtils.getToken(builder, mSTRING_CTOR_START)) {
      myBracket = mSTRING_CTOR_START;
    }

    if (myBracket == null) {
      bbm.rollbackTo();
      builder.error(GroovyBundle.message("lbrack.or.lparen.or.lcurly.or.string_ctor_start.expected"));
      return WRONGWAY;
    }

    balancedTokens = BalancedTokens.parse(builder);

    if (tWRONG_SET.contains(balancedTokens)) {
      bbm.rollbackTo();
      return WRONGWAY;
    }

    if (ParserUtils.getToken(builder, mRPAREN) && !mRPAREN.equals(Pairs.pairElementsMap.get(myBracket))
        || ParserUtils.getToken(builder, mRBRACK) && !mRBRACK.equals(Pairs.pairElementsMap.get(myBracket))
        || ParserUtils.getToken(builder, mRCURLY) && !mRCURLY.equals(Pairs.pairElementsMap.get(myBracket))
        || ParserUtils.getToken(builder, mSTRING_CTOR_END) && !mSTRING_CTOR_END.equals(Pairs.pairElementsMap.get(myBracket))) {
      bbm.rollbackTo();
      return WRONGWAY;
    } else {
      bbm.done(BALANCED_BRACKETS);
      return BALANCED_BRACKETS;
    }
  }
}
