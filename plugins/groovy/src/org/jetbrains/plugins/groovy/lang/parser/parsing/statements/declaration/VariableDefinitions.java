package org.jetbrains.plugins.groovy.lang.parser.parsing.statements.declaration;

import com.intellij.lang.PsiBuilder;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyElementType;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.parser.parsing.auxiliary.NlsWarn;
import org.jetbrains.plugins.groovy.lang.parser.parsing.auxiliary.ThrowClause;
import org.jetbrains.plugins.groovy.lang.parser.parsing.auxiliary.parameters.ParameterDeclarationList;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.blocks.OpenBlock;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.expressions.AssignmentExpression;
import org.jetbrains.plugins.groovy.lang.parser.parsing.util.ParserUtils;

/**
 * @autor: Dmitry.Krasilschikov
 * @date: 16.03.2007
 */
public class VariableDefinitions implements GroovyElementTypes {
  public static GroovyElementType parse(PsiBuilder builder) {
//    PsiBuilder.Marker vdMarker = builder.mark();

    if (!(ParserUtils.lookAhead(builder, mIDENT) || ParserUtils.lookAhead(builder, mSTRING_LITERAL))) {
      builder.error(GroovyBundle.message("indentifier.or.string.literal.expected"));
//      vdMarker.rollbackTo();
      return WRONGWAY;
    }

    PsiBuilder.Marker varMarker = builder.mark();
    if ((ParserUtils.getToken(builder, mIDENT) || ParserUtils.getToken(builder, mSTRING_LITERAL)) && ParserUtils.getToken(builder, mLPAREN)) {

      ParameterDeclarationList.parse(builder);
      if (!ParserUtils.getToken(builder, mRPAREN)) {
        ParserUtils.waitNextRCurly(builder);

        builder.error(GroovyBundle.message("rparen.expected"));
      }

      ThrowClause.parse(builder);

      NlsWarn.parse(builder);

      OpenBlock.parse(builder);

      varMarker.drop();
//      vdMarker.done(METHOD_DEFINITION);
      return METHOD_DEFINITION;
    } else {
      varMarker.rollbackTo();
    }

    if (parseVariableDeclarator(builder)) {
      while (ParserUtils.getToken(builder, mCOMMA)) {
        ParserUtils.getToken(builder, mNLS);

        parseVariableDeclarator(builder);
      }

//      vdMarker.done(VARIABLE_DEFINITION);
      return VARIABLE_DEFINITION;
    }


    builder.error(GroovyBundle.message("indentifier.or.string.literal.expected"));
//    vdMarker.rollbackTo();
    return WRONGWAY;

  }

  private static boolean parseVariableDeclarator(PsiBuilder builder) {
    if (!(ParserUtils.getToken(builder, mIDENT))) {
      return false;
    }

    if (ParserUtils.getToken(builder, mASSIGN)) {
      ParserUtils.getToken(builder, mNLS);
      if (WRONGWAY.equals(AssignmentExpression.parse(builder))) {
        return false;
      }
    }

    return true;
  }
}
