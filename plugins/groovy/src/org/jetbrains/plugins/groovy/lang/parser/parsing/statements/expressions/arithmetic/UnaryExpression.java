package org.jetbrains.plugins.groovy.lang.parser.parsing.statements.expressions.arithmetic;

import org.jetbrains.plugins.groovy.lang.lexer.GroovyElementType;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.parser.parsing.util.ParserUtils;
import org.jetbrains.plugins.groovy.GroovyBundle;
import com.intellij.lang.PsiBuilder;
import com.intellij.psi.tree.TokenSet;

/**
 * @author Ilya.Sergey
 */
public class UnaryExpression implements GroovyElementTypes {

  private static TokenSet PREFIXES = TokenSet.create(
          mPLUS,
          mMINUS,
          mINC,
          mDEC,
          mBNOT,
          mLNOT
  );

  public static GroovyElementType parse(PsiBuilder builder){

    PsiBuilder.Marker marker = builder.mark();
    if (ParserUtils.getToken(builder, PREFIXES)){
      ParserUtils.getToken(builder, mNLS);
      parse(builder);
      marker.done(UNARY_EXPRESSION);
      return UNARY_EXPRESSION;
    } else {
      marker.drop();
      GroovyElementType result = UnaryExpressionNotPlusMinus.parse(builder);
      if (result.equals(WRONGWAY)) {
        builder.error(GroovyBundle.message("expression.expected"));
      }
      return result; 
    }
  }

}