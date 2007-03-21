package org.jetbrains.plugins.groovy.lang.parser.parsing.statements.expressions.arithmetic;

import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.expressions.primary.PrimaryExpression;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.expressions.primary.StringConstructorExpression;
import org.jetbrains.plugins.groovy.lang.parser.parsing.util.ParserUtils;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyElementType;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;
import org.jetbrains.plugins.groovy.GroovyBundle;
import com.intellij.lang.PsiBuilder;
import com.intellij.psi.tree.TokenSet;

/**
 * @author Ilya.Sergey
 */
public class PathExpression implements GroovyElementTypes {

  public static GroovyElementType parse(PsiBuilder builder) {

    PsiBuilder.Marker marker = builder.mark();
    GroovyElementType result = PrimaryExpression.parse(builder);
    if (!WRONGWAY.equals(result)) {
      if (isPathElementStart(builder)) {
        PsiBuilder.Marker newMarker = marker.precede();
        marker.drop();
        pathElementParse(builder, newMarker);
        return PATH_EXPRESSION;
      } else {
        marker.drop();
      }
    } else {
      marker.drop();
    }
    return result;
  }

  private static GroovyElementType pathElementParse(PsiBuilder builder,
                                                    PsiBuilder.Marker marker) {
    TokenSet DOTS = TokenSet.create(
            mSPREAD_DOT,
            mOPTIONAL_DOT,
            mMEMBER_POINTER,
            mDOT
    );


    if (DOTS.contains(builder.getTokenType()) ||
            ParserUtils.lookAhead(builder, mNLS, mDOT)) {
      ParserUtils.getToken(builder, mNLS);
      ParserUtils.getToken(builder, DOTS);
      ParserUtils.getToken(builder, mNLS);

      // TODO Add type arguments parsing

      GroovyElementType res = namePartParse(builder);
      if (!res.equals(WRONGWAY)) {
        PsiBuilder.Marker newMarker = marker.precede();
        marker.done(PATH_EXPRESSION);
        pathElementParse(builder, newMarker);
      } else {
        builder.error(GroovyBundle.message("path.selector.expected"));
        marker.drop();
      }
    } else {

      // TODO add other cases

      marker.drop();
    }

    return PATH_EXPRESSION;
  }

  private static GroovyElementType namePartParse(PsiBuilder builder) {
    ParserUtils.getToken(builder, mAT);
    if (mIDENT.equals(builder.getTokenType())) {
      ParserUtils.eatElement(builder, PATH_NAME_PART);
      return PATH_NAME_PART;
    }
    if (mSTRING_LITERAL.equals(builder.getTokenType()) ||
            mGSTRING_LITERAL.equals(builder.getTokenType())) {
      ParserUtils.eatElement(builder, PATH_NAME_PART);
      return PATH_NAME_PART;
    }
    if (mGSTRING_SINGLE_BEGIN.equals(builder.getTokenType())) {
      StringConstructorExpression.parse(builder);
      return PATH_NAME_PART;
    }
    if (TokenSets.KEYWORD_PROPERTY_NAMES.contains(builder.getTokenType())) {
      ParserUtils.eatElement(builder, PATH_NAME_PART);
      return PATH_NAME_PART;
    }
    return WRONGWAY;
  }


  private static TokenSet PATH_ELEMENT_START = TokenSet.create(
          mSPREAD_DOT,
          mOPTIONAL_DOT,
          mMEMBER_POINTER,
          mLBRACK,
          mLPAREN,
          mLCURLY,
          mDOT
  );

  private static boolean isPathElementStart(PsiBuilder builder) {
    return (PATH_ELEMENT_START.contains(builder.getTokenType()) ||
            ParserUtils.lookAhead(builder, mNLS, mDOT) ||
            ParserUtils.lookAhead(builder, mNLS, mLCURLY));
  }

}
