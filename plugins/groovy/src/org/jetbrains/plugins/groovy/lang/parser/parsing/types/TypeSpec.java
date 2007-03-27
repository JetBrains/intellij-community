package org.jetbrains.plugins.groovy.lang.parser.parsing.types;

import com.intellij.lang.PsiBuilder;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyElementType;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.parser.parsing.util.ParserUtils;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.typeDefinitions.ClassOrInterfaceType;

/**
 * @autor: Dmitry.Krasilschikov
 * @date: 16.03.2007
 */
public class TypeSpec implements GroovyElementTypes {
  public static GroovyElementType parse(PsiBuilder builder) {
    if (TokenSets.BUILT_IN_TYPE.contains(builder.getTokenType())) {
      return parseBuiltInType(builder);
    }

    if (ParserUtils.lookAhead(builder, mIDENT)){
      return parseClassOrInterfaceType(builder);
    }

    return WRONGWAY;
  }

  /**
   * For built-in types
   *
   * @param builder
   * @return
   */
  private static GroovyElementType parseBuiltInType(PsiBuilder builder) {
    PsiBuilder.Marker arrMarker = builder.mark();
    ParserUtils.eatElement(builder, BUILT_IN_TYPE);
    if (mLBRACK.equals(builder.getTokenType())) {
      declarationBracketsParse(builder, arrMarker);
    } else {
      arrMarker.drop();
    }
    return TYPE_SPECIFICATION;
  }


  /**
   * For array definitions
   * 
   * @param builder
   * @param marker
   */
  private static void declarationBracketsParse(PsiBuilder builder, PsiBuilder.Marker marker) {
    ParserUtils.getToken(builder, mLBRACK);
    ParserUtils.getToken(builder, mRBRACK, GroovyBundle.message("rbrack.expected"));
    PsiBuilder.Marker newMarker = marker.precede();
    marker.done(ARRAY_TYPE);
    if (mLBRACK.equals(builder.getTokenType())) {
      declarationBracketsParse(builder, newMarker);
    } else {
      newMarker.drop();
    }
  }

  /*
   * Class or interface type
   * @param builder
   */

  private static GroovyElementType parseClassOrInterfaceType(PsiBuilder builder) {
    PsiBuilder.Marker arrMarker = builder.mark();

    if (WRONGWAY.equals(ClassOrInterfaceType.parse(builder))) {
      arrMarker.rollbackTo();
      return WRONGWAY;
    }

    if (mLBRACK.equals(builder.getTokenType())) {
      declarationBracketsParse(builder, arrMarker);
    } else {
      arrMarker.drop();
    }
    return TYPE_SPECIFICATION;
  }


}
