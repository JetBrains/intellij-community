/*
 * Copyright 2000-2007 JetBrains s.r.o.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.plugins.groovy.lang.parser.parsing.types;

import com.intellij.lang.PsiBuilder;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyElementType;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.typeDefinitions.ReferenceElement;
import org.jetbrains.plugins.groovy.lang.parser.parsing.util.ParserUtils;

/**
 * @autor: Dmitry.Krasilschikov
 * @date: 16.03.2007
 */
public class TypeSpec implements GroovyElementTypes {
  public static boolean parse(PsiBuilder builder) {
    return parse(builder, false); //allow lower and upper case first letter
  }

  public static boolean parse(PsiBuilder builder, boolean isUpper) {
    if (TokenSets.BUILT_IN_TYPE.contains(builder.getTokenType())) {
      return parseBuiltInType(builder);
    } else if (builder.getTokenType() == mIDENT) {
      return parseClassOrInterfaceType(builder, isUpper);
    }
    return false;
  }

  /**
   * For built-in types
   *
   * @param builder
   * @return
   */
  public static boolean parseBuiltInType(PsiBuilder builder) {
    PsiBuilder.Marker arrMarker = builder.mark();
    ParserUtils.eatElement(builder, BUILT_IN_TYPE);
    if (mLBRACK.equals(builder.getTokenType())) {
      declarationBracketsParse(builder, arrMarker);
    } else {
      arrMarker.drop();
    }
    return true;
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

  /**
   * Class or interface type
   *
   * @param builder
   */

  private static boolean parseClassOrInterfaceType(PsiBuilder builder, boolean isUpper) {
    PsiBuilder.Marker arrMarker = builder.mark();
    PsiBuilder.Marker typeElementMarker = builder.mark();

    if (!ReferenceElement.parseReferenceElement(builder, isUpper)) {
      typeElementMarker.drop();
      arrMarker.rollbackTo();
      return false;
    }

    typeElementMarker.done(CLASS_TYPE_ELEMENT);

    if (ParserUtils.lookAhead(builder, mLBRACK, mRBRACK)) {
      declarationBracketsParse(builder, arrMarker);
    } else {
      arrMarker.drop();
    }
    return true;
  }

  /**
   * ********************************************************************************************************
   * ****************  Strict type specification parsing
   * ********************************************************************************************************
   */

  /**
   * Strict parsing
   *
   * @param builder
   * @return
   */
  public static boolean parseStrict(PsiBuilder builder) {
    if (TokenSets.BUILT_IN_TYPE.contains(builder.getTokenType())) {
      return parseBuiltInTypeStrict(builder);
    } else if (builder.getTokenType() == mIDENT) {
      return parseClassOrInterfaceTypeStrict(builder);
    }
    return false;
  }


  /**
   * For built-in types
   *
   * @param builder
   * @return
   */
  private static boolean parseBuiltInTypeStrict(PsiBuilder builder) {
    PsiBuilder.Marker arrMarker = builder.mark();
    ParserUtils.eatElement(builder, BUILT_IN_TYPE);
    if (mLBRACK.equals(builder.getTokenType())) {
      return declarationBracketsParseStrict(builder, arrMarker);
    } else {
      arrMarker.drop();
      return true;
    }
  }


  /**
   * Strict parsing of array definitions
   *
   * @param builder
   * @param marker
   */
  private static boolean declarationBracketsParseStrict(PsiBuilder builder, PsiBuilder.Marker marker) {
    ParserUtils.getToken(builder, mLBRACK);
    if (!ParserUtils.getToken(builder, mRBRACK, GroovyBundle.message("rbrack.expected"))) {
      marker.rollbackTo();
      return false;
    }
    PsiBuilder.Marker newMarker = marker.precede();
    marker.done(ARRAY_TYPE);
    if (mLBRACK.equals(builder.getTokenType())) {
      return declarationBracketsParseStrict(builder, newMarker);
    } else {
      newMarker.drop();
      return true;
    }
  }


  /**
   * Strict class type parsing
   *
   * @param builder
   * @return
   */
  private static boolean parseClassOrInterfaceTypeStrict(PsiBuilder builder) {
    PsiBuilder.Marker arrMarker = builder.mark();
    PsiBuilder.Marker typeElementMarker = builder.mark();

    if (!ReferenceElement.parseReferenceElement(builder)) {
      typeElementMarker.drop();
      arrMarker.rollbackTo();
      return false;
    }

    typeElementMarker.done(CLASS_TYPE_ELEMENT);

    if (mLBRACK.equals(builder.getTokenType())) {
      return declarationBracketsParseStrict(builder, arrMarker);
    } else {
//      arrMarker.done(CLASS_TYPE_ELEMENT);
      arrMarker.drop();
      return true;
    }
  }
}
