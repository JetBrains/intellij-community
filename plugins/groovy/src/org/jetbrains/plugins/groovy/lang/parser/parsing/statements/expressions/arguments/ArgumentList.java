package org.jetbrains.plugins.groovy.lang.parser.parsing.statements.expressions.arguments;

import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.expressions.ExpressionStatement;
import org.jetbrains.plugins.groovy.lang.parser.parsing.util.ParserUtils;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyElementType;
import org.jetbrains.plugins.groovy.GroovyBundle;
import com.intellij.lang.PsiBuilder;
import com.intellij.psi.tree.TokenSet;

/**
 * @author Ilya.Sergey
 */
public class ArgumentList implements GroovyElementTypes {

  public static GroovyElementType parse(PsiBuilder builder) {

    PsiBuilder.Marker marker = builder.mark();
    if (ParserUtils.getToken(builder, mCOMMA)) {
      marker.done(ARGUMENTS);
      return ARGUMENTS;
    }

    boolean flag = true;
    GroovyElementType result = argumentParse(builder);
    if (result.equals(WRONGWAY)) {
      flag = cleanGarbageAfter(builder);
    }
    while (flag && !builder.eof()) {
      ParserUtils.getToken(builder, mNLS);
      boolean flag1 = ParserUtils.getToken(builder, mCOMMA);
      if (!flag1 && !argsFinishSoon(builder)) {
        builder.error(GroovyBundle.message("comma.expected"));
      }
      ParserUtils.getToken(builder, mNLS);
      result = argumentParse(builder);
      if (result.equals(WRONGWAY)) {
        flag = cleanGarbageAfter(builder);
      }
    }
    marker.done(ARGUMENTS);
    return ARGUMENTS;
  }


  private static boolean cleanGarbageAfter(PsiBuilder builder) {
    if (!argsFinishSoon(builder)) {
      PsiBuilder.Marker em = builder.mark();
      TokenSet FINISH = TokenSet.create(mRPAREN, mRBRACK, mCOMMA);
      if (mCOMMA.equals(builder.getTokenType())) {
        builder.error(GroovyBundle.message("argument.error"));
        em.drop();
        return true;
      }
      while (!FINISH.contains(builder.getTokenType())) {
        builder.advanceLexer();
      }
      em.error(GroovyBundle.message("argument.error"));
      if (mCOMMA.equals(builder.getTokenType())) {
        return true;
      } else {
        return false;
      }
    }
    return false;
  }

  private static boolean argsFinishSoon(PsiBuilder builder) {
    return ParserUtils.lookAhead(builder, mRPAREN) ||
            ParserUtils.lookAhead(builder, mRBRACK) ||
            ParserUtils.lookAhead(builder, mNLS, mRPAREN) ||
            ParserUtils.lookAhead(builder, mNLS, mRBRACK);
  }

  private static GroovyElementType argumentParse(PsiBuilder builder) {
    // TODO implement all variants!
    return ExpressionStatement.argParse(builder);
  }

}