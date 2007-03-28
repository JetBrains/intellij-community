package org.jetbrains.plugins.groovy.lang.parser.parsing.types;

import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.parser.parsing.util.ParserUtils;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.typeDefinitions.ClassOrInterfaceType;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyElementType;
import com.intellij.lang.PsiBuilder;

/**
 * @author: Dmitry.Krasilschikov
 * @date: 28.03.2007
 */
public class TypeArgument implements GroovyElementTypes {
  public static GroovyElementType parse(PsiBuilder builder) {
    PsiBuilder.Marker taMarker = builder.mark();
    //wildcard
    if (ParserUtils.getToken(builder, mQUESTION)) {
      if (ParserUtils.getToken(builder, kSUPER) || ParserUtils.getToken(builder, kEXTENDS)) {
        ParserUtils.getToken(builder, mNLS);

        if (WRONGWAY.equals(ClassOrInterfaceType.parse(builder))) {
          taMarker.rollbackTo();
          return WRONGWAY;
        }

        ParserUtils.getToken(builder, mNLS);
      } else {
        taMarker.rollbackTo();
        return WRONGWAY;
      }

      taMarker.done(TYPE_ARGUMENT);
      return TYPE_ARGUMENT;
    }

    //typeSpec
    if (WRONGWAY.equals(TypeSpec.parse(builder))) {
      taMarker.rollbackTo();
      return WRONGWAY;
    } else {
      taMarker.done(TYPE_ARGUMENT);
      return TYPE_ARGUMENT;
    }
  }
}
