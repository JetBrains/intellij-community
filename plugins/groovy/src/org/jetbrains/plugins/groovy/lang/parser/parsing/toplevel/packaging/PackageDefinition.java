package org.jetbrains.plugins.groovy.lang.parser.parsing.toplevel.packaging;

import org.jetbrains.plugins.groovy.lang.parser.parsing.Construction;
import org.jetbrains.plugins.groovy.lang.parser.parsing.auxiliary.Identifier;
import org.jetbrains.plugins.groovy.lang.parser.parsing.util.ParserUtils;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyElementType;
import org.jetbrains.plugins.groovy.GroovyBundle;
import com.intellij.lang.PsiBuilder;
import com.intellij.lang.PsiBuilder.*;


/**
 * @author Ilya.Sergey
 */
public class PackageDefinition implements Construction {

  public static GroovyElementType parse(PsiBuilder builder) {

    // TODO Add annotation parsing

    Marker pMarker = builder.mark();

    if (!ParserUtils.getToken(builder, kPACKAGE, GroovyBundle.message("package.keyword.expected"))){
      pMarker.drop();
      return WRONGWAY;
    }
    if (ParserUtils.lookAhead(builder, mIDENT)){
      Identifier.parse(builder);
    } else {
      builder.error(GroovyBundle.message("identifier.expected"));
    }

    pMarker.done(PACKAGE_DEFINITION);
    return PACKAGE_DEFINITION;
  }
}
