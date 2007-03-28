package org.jetbrains.plugins.groovy.lang.parser.parsing.types;

import org.jetbrains.plugins.groovy.lang.lexer.GroovyElementType;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.parser.parsing.util.ParserUtils;
import com.intellij.lang.PsiBuilder;

/**
 * @author: Dmitry.Krasilschikov
 * @date: 28.03.2007
 */
public class TypeArguments implements GroovyElementTypes {
  public static GroovyElementType parse(PsiBuilder builder) {
    PsiBuilder.Marker taMarker = builder.mark();

    if (!ParserUtils.getToken(builder, mLT)) {
      taMarker.rollbackTo();
      return WRONGWAY;
    }

    ParserUtils.getToken(builder, mNLS);

    if (WRONGWAY.equals(TypeArgument.parse(builder))) {
      taMarker.rollbackTo();
      return WRONGWAY;
    }

    while (ParserUtils.getToken(builder, mCOMMA)) {
      ParserUtils.getToken(builder, mNLS);

      if (WRONGWAY.equals(TypeArgument.parse(builder))) {
        taMarker.done(TYPE_ARGUMENTS);
        return TYPE_ARGUMENTS;
      }
    }

    ParserUtils.getToken(builder, mNLS);

    if (ParserUtils.getToken(builder, mGT) || ParserUtils.getToken(builder, mSR) || ParserUtils.getToken(builder, mBSR)) {
      ParserUtils.getToken(builder, mNLS);
    }

    taMarker.done(TYPE_ARGUMENTS);
    return TYPE_ARGUMENTS;
  }
}
