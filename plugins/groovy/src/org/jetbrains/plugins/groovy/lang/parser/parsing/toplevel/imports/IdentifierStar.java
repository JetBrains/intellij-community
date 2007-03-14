package org.jetbrains.plugins.groovy.lang.parser.parsing.toplevel.imports;

import org.jetbrains.plugins.groovy.lang.parser.parsing.Construction;
import org.jetbrains.plugins.groovy.lang.parser.parsing.util.ParserUtils;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyElementType;
import org.jetbrains.plugins.groovy.GroovyBundle;


import com.intellij.lang.PsiBuilder;
import com.intellij.lang.PsiBuilder.Marker;

/**
 * Import identifier
 *
 * @author Ilya Sergey
 */
public class IdentifierStar implements Construction {

  public static GroovyElementType parse(PsiBuilder builder) {

    Marker idMarker = builder.mark();

    if (ParserUtils.getToken(builder, mIDENT, GroovyBundle.message("identifier.expected"))) {
      Marker newMarker = idMarker.precede();
      idMarker.done(IDENITFIER_STAR);
      subParse(builder, newMarker);
    }
    return IDENITFIER_STAR;
  }

  private static void subParse(PsiBuilder builder, Marker marker) {
    while (ParserUtils.getToken(builder, mDOT)) {
      if (ParserUtils.lookAhead(builder, mIDENT, mDOT)) {
        builder.advanceLexer();
        Marker newMarker = marker.precede();
        marker.done(IDENITFIER_STAR);
        subParse(builder, newMarker);
      } else if (ParserUtils.lookAhead(builder, mSTAR)){
        builder.advanceLexer();
        //marker.
      }
    }
  }

}
