package org.jetbrains.plugins.groovy.lang.parser.parsing.statements.typeDefinitions.members;

import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.parser.parsing.util.ParserUtils;
import org.jetbrains.plugins.groovy.lang.parser.parsing.auxiliary.parameters.ParameterDeclarationList;
import org.jetbrains.plugins.groovy.lang.parser.parsing.auxiliary.ThrowClause;
import org.jetbrains.plugins.groovy.lang.parser.parsing.auxiliary.NlsWarn;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.constructor.MethodBody;
import org.jetbrains.plugins.groovy.GroovyBundle;
import com.intellij.psi.tree.IElementType;
import com.intellij.lang.PsiBuilder;

/**
 * @author: Dmitry.Krasilschikov
 * @date: 23.03.2007
 */
public class MethodDefinition implements GroovyElementTypes {
  public static IElementType parse(PsiBuilder builder) {
    PsiBuilder.Marker constrMarker = builder.mark();

    if (!ParserUtils.getToken(builder, mIDENT)) {
      constrMarker.rollbackTo();
      return WRONGWAY;
    }

    if (!ParserUtils.getToken(builder, mLPAREN)) {
      constrMarker.rollbackTo();
      builder.error(GroovyBundle.message("lparen.expected"));
      return WRONGWAY;
    }

    if (ParserUtils.lookAhead(builder, kFINAL) || ParserUtils.lookAhead(builder, kDEF) || ParserUtils.lookAhead(builder, mAT)) {
      ParameterDeclarationList.parse(builder);
    }

    if (!ParserUtils.getToken(builder, mRPAREN)) {
      constrMarker.rollbackTo();
      builder.error(GroovyBundle.message("rparen.expected"));
      return WRONGWAY;
    }

    ThrowClause.parse(builder);

    NlsWarn.parse(builder);

    IElementType methodBody = MethodBody.parse(builder);

    if (METHOD_BODY.equals(methodBody)) {
      constrMarker.done(METHOD_DEFINITION);
      return METHOD_DEFINITION;
    } else if (CONSTRUCTOR_BODY.equals(methodBody)) {
      constrMarker.done(CONSTRUCTOR_DEFINITION);
      return CONSTRUCTOR_DEFINITION;
    } else {
      constrMarker.rollbackTo();
      return WRONGWAY;
    }
  }
}