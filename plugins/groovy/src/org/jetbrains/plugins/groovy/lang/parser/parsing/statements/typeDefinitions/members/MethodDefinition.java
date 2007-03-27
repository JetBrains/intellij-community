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
    if (!ParserUtils.getToken(builder, mIDENT)) {
      builder.error(GroovyBundle.message("identifier.expected"));
      return WRONGWAY;
    }

    if (!ParserUtils.getToken(builder, mLPAREN)) {
      builder.error(GroovyBundle.message("lparen.expected"));
    }

    ParameterDeclarationList.parse(builder);

    if (!ParserUtils.getToken(builder, mRPAREN)) {
      ParserUtils.waitNextRCurly(builder);

      builder.error(GroovyBundle.message("rparen.expected"));
    }

    ThrowClause.parse(builder);

    NlsWarn.parse(builder);

    IElementType methodBody = MethodBody.parse(builder);

    if (METHOD_BODY.equals(methodBody)) {
      return METHOD_DEFINITION;
    } else if (CONSTRUCTOR_BODY.equals(methodBody)) {
      return CONSTRUCTOR_DEFINITION;
    } else {
      return WRONGWAY;
    }
  }
}