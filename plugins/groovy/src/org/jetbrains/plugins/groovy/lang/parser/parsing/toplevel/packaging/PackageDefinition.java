package org.jetbrains.plugins.groovy.lang.parser.parsing.toplevel.packaging;

import org.jetbrains.plugins.groovy.lang.parser.parsing.auxiliary.Identifier;
import org.jetbrains.plugins.groovy.lang.parser.parsing.util.ParserUtils;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyElementType;
import org.jetbrains.plugins.groovy.GroovyBundle;
import com.intellij.lang.PsiBuilder;
import com.intellij.lang.PsiBuilder.*;


/**
 * @author Ilya.Sergey
 */
public class PackageDefinition implements GroovyElementTypes {

  public static GroovyElementType parse(PsiBuilder builder) {

    // TODO Add annotation parsing

    Marker pMarker = builder.mark();

    if (!ParserUtils.getToken(builder, kPACKAGE, GroovyBundle.message("package.keyword.expected"))) {
      pMarker.drop();
      return WRONGWAY;
    }
    if (ParserUtils.lookAhead(builder, mIDENT)) {
      identifierParse(builder);
    } else {
      builder.error(GroovyBundle.message("identifier.expected"));
    }

    pMarker.done(PACKAGE_DEFINITION);
    return PACKAGE_DEFINITION;
  }

  private static GroovyElementType identifierParse(PsiBuilder builder) {

    Marker marker = builder.mark();
    if (!ParserUtils.getToken(builder, mIDENT, GroovyBundle.message("identifier.expected"))) {
      marker.rollbackTo();
      return WRONGWAY;
    }

    boolean flag = true;
    while (flag && ParserUtils.getToken(builder, mDOT)) {
      if (ParserUtils.lookAhead(builder, mNLS, mIDENT)) {
        ParserUtils.getToken(builder, mNLS);
      }
      flag = ParserUtils.getToken(builder, mIDENT, GroovyBundle.message("identifier.expected"));
    }
    marker.done(IDENTIFIER);

    return IDENTIFIER;
  }
}
