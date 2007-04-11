package org.jetbrains.plugins.groovy.lang.parser.parsing.statements.typeDefinitions.members;

import com.intellij.lang.PsiBuilder;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyElementType;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.parser.parsing.util.ParserUtils;

/**
 * @author: Dmitry.Krasilschikov
 * @date: 06.04.2007
 */
public class EnumConstants implements GroovyElementTypes {
  public static GroovyElementType parse(PsiBuilder builder) {
    PsiBuilder.Marker enumConstantsMarker = builder.mark();

    if (WRONGWAY.equals(EnumConstant.parse(builder))) {
      return WRONGWAY;
    }

    while (ParserUtils.getToken(builder, mCOMMA)) {
      EnumConstant.parse(builder);
    }

    ParserUtils.getToken(builder, mCOMMA);

    enumConstantsMarker.done(ENUM_CONSTANTS);
    return ENUM_CONSTANTS;
  }
}
