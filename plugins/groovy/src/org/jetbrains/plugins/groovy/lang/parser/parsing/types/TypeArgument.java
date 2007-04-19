package org.jetbrains.plugins.groovy.lang.parser.parsing.types;

import com.intellij.lang.PsiBuilder;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyElementType;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.typeDefinitions.ReferenceElement;
import org.jetbrains.plugins.groovy.lang.parser.parsing.util.ParserUtils;

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

        //todo: check for upper case type specification
        if (WRONGWAY.equals(ReferenceElement.parse(builder))) {
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
    //todo: check for upper case type specification
    if (WRONGWAY.equals(TypeSpec.parse(builder))) {
      taMarker.rollbackTo();
      return WRONGWAY;
    } else {
      taMarker.done(TYPE_ARGUMENT);
      return TYPE_ARGUMENT;
    }
  }
}
