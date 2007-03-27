package org.jetbrains.plugins.groovy.lang.parser.parsing.statements.expressions.arguments;

import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.expressions.ExpressionStatement;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.expressions.primary.StringConstructorExpression;
import org.jetbrains.plugins.groovy.lang.parser.parsing.util.ParserUtils;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyElementType;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;
import org.jetbrains.plugins.groovy.GroovyBundle;
import com.intellij.lang.PsiBuilder;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.tree.IElementType;

/**
 * @author Ilya.Sergey
 */
public class ArgumentList implements GroovyElementTypes {

  /**
   * Parsing argument list
   *
   * @param builder
   * @param closingBrace
   * @return
   */
  public static GroovyElementType parse(PsiBuilder builder, IElementType closingBrace) {

    PsiBuilder.Marker marker = builder.mark();
    if (ParserUtils.getToken(builder, mCOMMA)) {
      marker.done(ARGUMENTS);
      return ARGUMENTS;
    }

    GroovyElementType result = argumentParse(builder);
    if (result.equals(WRONGWAY)) {
      if (!closingBrace.equals(builder.getTokenType())){
        builder.error(GroovyBundle.message("expression.expected"));
      }
      if (!mCOMMA.equals(builder.getTokenType()) &&
              !closingBrace.equals(builder.getTokenType())) {
        builder.advanceLexer();
      }
    }
    while (!builder.eof() && !closingBrace.equals(builder.getTokenType())) {
      ParserUtils.getToken(builder, mNLS);
      ParserUtils.getToken(builder, mCOMMA, GroovyBundle.message("comma.expected"));
      ParserUtils.getToken(builder, mNLS);
      if (argumentParse(builder).equals(WRONGWAY)) {
        if (!closingBrace.equals(builder.getTokenType())){
          builder.error(GroovyBundle.message("expression.expected"));
        }
        if (!mCOMMA.equals(builder.getTokenType()) &&
                !closingBrace.equals(builder.getTokenType())) {
          builder.advanceLexer();
        }
      }
      ParserUtils.getToken(builder, mNLS);
    }

    marker.done(ARGUMENTS);
    return ARGUMENTS;
  }


  private static GroovyElementType argumentParse(PsiBuilder builder) {
    // TODO implement all variants!
    return ExpressionStatement.argParse(builder);
  }

  /**
   * Checks for atgument label
   *
   * @param builder
   * @return
   * @param dropMarker True if must drop this marker, false for rollback
   */
  public static boolean argumentLabelStartCheck(PsiBuilder builder, boolean dropMarker) {

    //TODO add cale with LPAREN token

    if (ParserUtils.lookAhead(builder, mIDENT, mCOLON) ||
            TokenSets.KEYWORD_PROPERTY_NAMES.contains(builder.getTokenType()) ||
            mNUM_INT.equals(builder.getTokenType()) ||
            mSTRING_LITERAL.equals(builder.getTokenType()) ||
            mGSTRING_LITERAL.equals(builder.getTokenType())
            ) {
      PsiBuilder.Marker marker = builder.mark();
      builder.advanceLexer();
      boolean itIs = mCOLON.equals(builder.getTokenType());
      if (dropMarker) {
        marker.drop();
      } else {
        marker.rollbackTo();
      }
      return itIs;
    } else if (mGSTRING_SINGLE_BEGIN.equals(builder.getTokenType())) {
      PsiBuilder.Marker marker = builder.mark();
      StringConstructorExpression.parse(builder);
      boolean itIs = mCOLON.equals(builder.getTokenType());
      if (dropMarker) {
        marker.drop();
      } else {
        marker.rollbackTo();
      }
      return itIs;
    }


    return false;
  }

}