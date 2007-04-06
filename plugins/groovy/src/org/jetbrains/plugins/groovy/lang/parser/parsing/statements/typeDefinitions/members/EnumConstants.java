package org.jetbrains.plugins.groovy.lang.parser.parsing.statements.typeDefinitions.members;

import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.parser.parsing.util.ParserUtils;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyElementType;
import com.intellij.lang.PsiBuilder;

/**
 * @author: Dmitry.Krasilschikov
 * @date: 06.04.2007
 */
public class EnumConstants implements GroovyElementTypes {
  public static GroovyElementType parse(PsiBuilder builder) {
    PsiBuilder.Marker enumConstantsMarker = builder.mark();

    EnumConstant.parse(builder);

    while (ParserUtils.getToken(builder, mCOMMA) || ParserUtils.getToken(builder, mSEMI)) {
      ParserUtils.getToken(builder, mNLS);

      EnumConstant.parse(builder);
    }
    
    ParserUtils.getToken(builder, mCOMMA);
    ParserUtils.getToken(builder, mNLS);

    enumConstantsMarker.done(ENUM_CONSTANTS);
    return ENUM_CONSTANTS;
  }
}
