package org.jetbrains.plugins.groovy.lang.parser.parsing.auxiliary.parameters;

import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.parser.parsing.util.ParserUtils;
import org.jetbrains.plugins.groovy.lang.parser.parsing.auxiliary.modifiers.ParameterModifierOptional;
import org.jetbrains.plugins.groovy.lang.parser.parsing.auxiliary.VariableInitializer;
import org.jetbrains.plugins.groovy.lang.parser.parsing.types.TypeSpec;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyElementType;
import com.intellij.psi.tree.IElementType;
import com.intellij.lang.PsiBuilder;

/**
 * @author: Dmitry.Krasilschikov
 * @date: 26.03.2007
 */
public class ParameterDeclaration implements GroovyElementTypes {
  public static IElementType parse(PsiBuilder builder) {
    PsiBuilder.Marker pdMarker = builder.mark();

    if (WRONGWAY.equals(ParameterModifierOptional.parse(builder))) {
      pdMarker.rollbackTo();
      return WRONGWAY;
    }

    PsiBuilder.Marker checkMarker = builder.mark();

    GroovyElementType type = TypeSpec.parse(builder);
    if (!WRONGWAY.equals(type)) { //type was recognized
      ParserUtils.getToken(builder, mTRIPLE_DOT);

      if (!ParserUtils.getToken(builder, mIDENT)) { //if there is no identifier rollback to begin
        checkMarker.rollbackTo();

        if (!ParserUtils.getToken(builder, mIDENT)) { //parse identifier because suggestion about type was wrong
          pdMarker.rollbackTo();
          return WRONGWAY;
        }

        VariableInitializer.parse(builder);

        pdMarker.done(PARAMETER);
        return PARAMETER;
      } else { //parse typized identifier
        checkMarker.drop();
        VariableInitializer.parse(builder);

        pdMarker.done(PARAMETER);
        return PARAMETER;
      }
    } else {
      //TODO:
      return WRONGWAY;
    }
  }
}
