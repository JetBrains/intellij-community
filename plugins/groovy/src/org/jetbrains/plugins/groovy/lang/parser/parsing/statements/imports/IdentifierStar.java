package org.jetbrains.plugins.groovy.lang.parser.parsing.statements.imports;

import org.jetbrains.plugins.groovy.lang.parser.parsing.util.ParserUtils;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyElementType;
import org.jetbrains.plugins.groovy.GroovyBundle;

import com.intellij.lang.PsiBuilder;
import com.intellij.lang.PsiBuilder.Marker;

/**
 * Import identifier
 *
 * @author Ilya Sergey
 */
public class IdentifierStar implements GroovyElementTypes {

  public static GroovyElementType parse(PsiBuilder builder) {

    Marker idMarker = builder.mark();

    if (ParserUtils.getToken(builder, mIDENT, GroovyBundle.message("identifier.expected"))) {
      if (ParserUtils.lookAhead(builder, mDOT)) {
        Marker newMarker = idMarker.precede();
        idMarker.done(IDENITFIER_STAR);
        subParse(builder, newMarker);
      } else if (ParserUtils.lookAhead(builder, kAS)) {
        builder.advanceLexer();
        if (ParserUtils.lookAhead(builder, mNLS, mIDENT)) {
          ParserUtils.getToken(builder, mNLS);
        }
        ParserUtils.getToken(builder, mIDENT, GroovyBundle.message("identifier.expected"));
        idMarker.done(IMPORT_SELECTOR);
      } else {
        idMarker.done(IMPORT_END);
      }
    } else {
      idMarker.drop();
    }
    return IDENITFIER_STAR;
  }

  private static void subParse(PsiBuilder builder, Marker marker) {
    ParserUtils.getToken(builder, mDOT);
    if (ParserUtils.lookAhead(builder, mIDENT, mDOT) ||
            ParserUtils.lookAhead(builder, mNLS, mIDENT, mDOT)) {
      ParserUtils.getToken(builder, mNLS);
      builder.advanceLexer();
      Marker newMarker = marker.precede();
      marker.done(IDENITFIER_STAR);
      subParse(builder, newMarker);
    } else if (ParserUtils.lookAhead(builder, mSTAR) ||
            ParserUtils.lookAhead(builder, mNLS, mSTAR)) {
      ParserUtils.getToken(builder, mNLS);
      builder.advanceLexer();
      marker.done(IDENITFIER_STAR);
    } else if (ParserUtils.lookAhead(builder, mIDENT, kAS) ||
            ParserUtils.lookAhead(builder, mNLS, mIDENT, kAS)) {
      marker.drop();
      ParserUtils.getToken(builder, mNLS);
      Marker selMarker = builder.mark();
      builder.advanceLexer();
      builder.getTokenText(); // eat identifier and pick lexer
      builder.advanceLexer(); // as
      if (ParserUtils.lookAhead(builder, mNLS, mIDENT)) {
        ParserUtils.getToken(builder, mNLS);
      }
      ParserUtils.getToken(builder, mIDENT, GroovyBundle.message("identifier.expected"));
      selMarker.done(IMPORT_SELECTOR);
    } else if (ParserUtils.lookAhead(builder, mIDENT) ||
            ParserUtils.lookAhead(builder, mNLS, mIDENT)) {
      marker.drop();
      ParserUtils.getToken(builder, mNLS);
      ParserUtils.eatElement(builder, IMPORT_END);
    } else {
      builder.error(GroovyBundle.message("identifier.expected"));
      marker.done(IDENITFIER_STAR);
    }
  }

}
