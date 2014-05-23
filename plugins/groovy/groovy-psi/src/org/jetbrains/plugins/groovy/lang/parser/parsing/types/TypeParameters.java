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
import com.intellij.psi.tree.IElementType;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.typeDefinitions.ReferenceElement;
import org.jetbrains.plugins.groovy.lang.parser.parsing.util.ParserUtils;

/**
 * @autor: ilyas
 */
public class TypeParameters {

  public static IElementType parse(PsiBuilder builder) {
    if (GroovyTokenTypes.mLT == builder.getTokenType()) {
      PsiBuilder.Marker marker = builder.mark();
      ParserUtils.getToken(builder, GroovyTokenTypes.mLT);
      ParserUtils.getToken(builder, GroovyTokenTypes.mNLS);
      while (parseTypeParameter(builder) != GroovyElementTypes.WRONGWAY) {
        if (!ParserUtils.getToken(builder, GroovyTokenTypes.mCOMMA)) {
          break;
        }
        ParserUtils.getToken(builder, GroovyTokenTypes.mNLS);
        eatCommas(builder);
      }
      eatCommas(builder);
      if (!ParserUtils.getToken(builder, GroovyTokenTypes.mGT)) {
        builder.error(GroovyBundle.message("gt.expected"));
      }
      marker.done(GroovyElementTypes.TYPE_PARAMETER_LIST);
      return GroovyElementTypes.TYPE_PARAMETER_LIST;
    }

    return GroovyElementTypes.WRONGWAY;
  }

  private static void eatCommas(PsiBuilder builder) {
    while (GroovyTokenTypes.mCOMMA == builder.getTokenType()) {
      builder.error(GroovyBundle.message("type.parameter.expected"));
      ParserUtils.getToken(builder, GroovyTokenTypes.mCOMMA);
      ParserUtils.getToken(builder, GroovyTokenTypes.mNLS);
    }
  }


  private static IElementType parseTypeParameter(PsiBuilder builder) {
    if (GroovyTokenTypes.mIDENT == builder.getTokenType()) {
      PsiBuilder.Marker marker = builder.mark();
      ParserUtils.getToken(builder, GroovyTokenTypes.mIDENT);
      if (GroovyTokenTypes.kEXTENDS == builder.getTokenType()) {
        parseExtendsBoundList(builder);
      } else {
        builder.mark().done(GroovyElementTypes.TYPE_PARAMETER_EXTENDS_BOUND_LIST);
      }
      marker.done(GroovyElementTypes.TYPE_PARAMETER);
      return GroovyElementTypes.TYPE_PARAMETER;
    }
    return GroovyElementTypes.WRONGWAY;
  }


  private static IElementType parseExtendsBoundList(PsiBuilder builder) {
    PsiBuilder.Marker marker = builder.mark();
    ParserUtils.getToken(builder, GroovyTokenTypes.kEXTENDS);
    ParserUtils.getToken(builder, GroovyTokenTypes.mNLS);
    if (ReferenceElement.parseReferenceElement(builder) == ReferenceElement.ReferenceElementResult.FAIL) {
      builder.error(GroovyBundle.message("identifier.expected"));
    } else {
      while (ParserUtils.getToken(builder, GroovyTokenTypes.mBAND)) {
        ParserUtils.getToken(builder, GroovyTokenTypes.mNLS);
        if (ReferenceElement.parseReferenceElement(builder) == ReferenceElement.ReferenceElementResult.FAIL) {
          builder.error(GroovyBundle.message("type.expected"));
        }
      }
    }
    marker.done(GroovyElementTypes.TYPE_PARAMETER_EXTENDS_BOUND_LIST);
    return GroovyElementTypes.TYPE_PARAMETER_EXTENDS_BOUND_LIST;
  }
}
