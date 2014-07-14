/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
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
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.typeDefinitions.ReferenceElement;
import org.jetbrains.plugins.groovy.lang.parser.parsing.util.ParserUtils;

/**
 * @autor: Dmitry.Krasilschikov
 * @date: 16.03.2007
 */
public class TypeSpec {
  public static ReferenceElement.ReferenceElementResult parse(PsiBuilder builder) {
    return parse(builder, false, true); //allow lower and upper case first letter
  }

  public static ReferenceElement.ReferenceElementResult parse(PsiBuilder builder, boolean isUpper, final boolean expressionPossible) {
    if (TokenSets.BUILT_IN_TYPES.contains(builder.getTokenType())) {
      return parseBuiltInType(builder);
    }
    if (builder.getTokenType() == GroovyTokenTypes.mIDENT) {
      return parseClassType(builder, isUpper, expressionPossible);
    }
    return ReferenceElement.ReferenceElementResult.FAIL;
  }

  /**
   * For built-in types
   *
   * @param builder
   * @return
   */
  public static ReferenceElement.ReferenceElementResult parseBuiltInType(PsiBuilder builder) {
    PsiBuilder.Marker arrMarker = builder.mark();
    ParserUtils.eatElement(builder, GroovyElementTypes.BUILT_IN_TYPE);
    if (GroovyTokenTypes.mLBRACK.equals(builder.getTokenType())) {
      declarationBracketsParse(builder, arrMarker);
    } else {
      arrMarker.drop();
    }
    return ReferenceElement.ReferenceElementResult.REF_WITH_TYPE_PARAMS;
  }


  /**
   * For array definitions
   *
   * @param builder
   * @param marker
   */
  private static void declarationBracketsParse(PsiBuilder builder, PsiBuilder.Marker marker) {
    ParserUtils.getToken(builder, GroovyTokenTypes.mLBRACK);
    ParserUtils.getToken(builder, GroovyTokenTypes.mRBRACK, GroovyBundle.message("rbrack.expected"));
    PsiBuilder.Marker newMarker = marker.precede();
    marker.done(GroovyElementTypes.ARRAY_TYPE);
    if (GroovyTokenTypes.mLBRACK.equals(builder.getTokenType())) {
      declarationBracketsParse(builder, newMarker);
    } else {
      newMarker.drop();
    }
  }

  private static ReferenceElement.ReferenceElementResult parseClassType(PsiBuilder builder, boolean isUpper, final boolean expressionPossible) {
    PsiBuilder.Marker arrMarker = builder.mark();
    PsiBuilder.Marker typeElementMarker = builder.mark();

    final ReferenceElement.ReferenceElementResult result = ReferenceElement.parseReferenceElement(builder, isUpper, expressionPossible);
    if (result == ReferenceElement.ReferenceElementResult.FAIL) {
      typeElementMarker.drop();
      arrMarker.rollbackTo();
      return ReferenceElement.ReferenceElementResult.FAIL;
    }

    typeElementMarker.done(GroovyElementTypes.CLASS_TYPE_ELEMENT);

    if (ParserUtils.lookAhead(builder, GroovyTokenTypes.mLBRACK, GroovyTokenTypes.mRBRACK)) {
      declarationBracketsParse(builder, arrMarker);
      return ReferenceElement.ReferenceElementResult.REF_WITH_TYPE_PARAMS;
    } else {
      arrMarker.drop();
      return result;
    }
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
  public static ReferenceElement.ReferenceElementResult parseStrict(PsiBuilder builder, boolean expressionPossible) {
    if (TokenSets.BUILT_IN_TYPES.contains(builder.getTokenType())) {
      return parseBuiltInTypeStrict(builder);
    }
    else if (builder.getTokenType() == GroovyTokenTypes.mIDENT) {
      return parseClassOrInterfaceTypeStrict(builder, expressionPossible);
    }
    return ReferenceElement.ReferenceElementResult.FAIL;
  }


  /**
   * For built-in types
   *
   * @param builder
   * @return
   */
  private static ReferenceElement.ReferenceElementResult parseBuiltInTypeStrict(PsiBuilder builder) {
    PsiBuilder.Marker arrMarker = builder.mark();
    ParserUtils.eatElement(builder, GroovyElementTypes.BUILT_IN_TYPE);
    if (GroovyTokenTypes.mLBRACK.equals(builder.getTokenType())) {
      return declarationBracketsParseStrict(builder, arrMarker) ? ReferenceElement.ReferenceElementResult.REF_WITH_TYPE_PARAMS
                                                                : ReferenceElement.ReferenceElementResult.FAIL;
    } else {
      arrMarker.drop();
      return ReferenceElement.ReferenceElementResult.REF_WITH_TYPE_PARAMS;
    }
  }


  /**
   * Strict parsing of array definitions
   *
   * @param builder
   * @param marker
   */
  private static boolean declarationBracketsParseStrict(PsiBuilder builder, PsiBuilder.Marker marker) {
    ParserUtils.getToken(builder, GroovyTokenTypes.mLBRACK);
    if (!ParserUtils.getToken(builder, GroovyTokenTypes.mRBRACK, GroovyBundle.message("rbrack.expected"))) {
      marker.rollbackTo();
      return false;
    }
    PsiBuilder.Marker newMarker = marker.precede();
    marker.done(GroovyElementTypes.ARRAY_TYPE);
    if (GroovyTokenTypes.mLBRACK.equals(builder.getTokenType())) {
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
  private static ReferenceElement.ReferenceElementResult parseClassOrInterfaceTypeStrict(PsiBuilder builder, boolean expressionPossible) {
    PsiBuilder.Marker arrMarker = builder.mark();
    PsiBuilder.Marker typeElementMarker = builder.mark();

    final ReferenceElement.ReferenceElementResult result = ReferenceElement.parseReferenceElement(builder, false, expressionPossible);
    if (result == ReferenceElement.ReferenceElementResult.FAIL) {
      typeElementMarker.drop();
      arrMarker.rollbackTo();
      return result;
    }

    typeElementMarker.done(GroovyElementTypes.CLASS_TYPE_ELEMENT);

    if (GroovyTokenTypes.mLBRACK.equals(builder.getTokenType())) {
      return declarationBracketsParseStrict(builder, arrMarker) ? ReferenceElement.ReferenceElementResult.REF_WITH_TYPE_PARAMS
                                                                : ReferenceElement.ReferenceElementResult.FAIL;
    }
    else {
      arrMarker.drop();
      return result;
    }
  }
}
