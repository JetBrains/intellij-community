/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.typeDefinitions.ReferenceElement;
import org.jetbrains.plugins.groovy.lang.parser.parsing.util.ParserUtils;

import static org.jetbrains.plugins.groovy.lang.parser.parsing.statements.typeDefinitions.ReferenceElement.ReferenceElementResult.fail;

/**
 * @autor: ilyas
 */
public class TypeParameters implements GroovyElementTypes {

  public static IElementType parse(PsiBuilder builder) {
    if (mLT == builder.getTokenType()) {
      PsiBuilder.Marker marker = builder.mark();
      ParserUtils.getToken(builder, mLT);
      ParserUtils.getToken(builder, mNLS);
      while (parseTypeParameter(builder) != WRONGWAY) {
        if (!ParserUtils.getToken(builder, mCOMMA)) {
          break;
        }
        ParserUtils.getToken(builder, mNLS);
        eatCommas(builder);
      }
      eatCommas(builder);
      if (!ParserUtils.getToken(builder, mGT)) {
        builder.error(GroovyBundle.message("gt.expected"));
      }
      marker.done(TYPE_PARAMETER_LIST);
      return TYPE_PARAMETER_LIST;
    }

    return WRONGWAY;
  }

  private static void eatCommas(PsiBuilder builder) {
    while (mCOMMA == builder.getTokenType()) {
      builder.error(GroovyBundle.message("type.parameter.expected"));
      ParserUtils.getToken(builder, mCOMMA);
      ParserUtils.getToken(builder, mNLS);
    }
  }


  private static IElementType parseTypeParameter(PsiBuilder builder) {
    if (mIDENT == builder.getTokenType()) {
      PsiBuilder.Marker marker = builder.mark();
      ParserUtils.getToken(builder, mIDENT);
      if (kEXTENDS == builder.getTokenType()) {
        parseExtendsBoundList(builder);
      } else {
        builder.mark().done(TYPE_PARAMETER_EXTENDS_BOUND_LIST);
      }
      marker.done(TYPE_PARAMETER);
      return TYPE_PARAMETER;
    }
    return WRONGWAY;
  }


  private static IElementType parseExtendsBoundList(PsiBuilder builder) {
    PsiBuilder.Marker marker = builder.mark();
    ParserUtils.getToken(builder, kEXTENDS);
    ParserUtils.getToken(builder, mNLS);
    if (ReferenceElement.parseReferenceElement(builder) == fail) {
      builder.error(GroovyBundle.message("identifier.expected"));
    } else {
      while (mBAND == builder.getTokenType()) {
        ParserUtils.getToken(builder, mBAND);
        ParserUtils.getToken(builder, mNLS);
        if (ReferenceElement.parseReferenceElement(builder) == fail) {
          builder.error(GroovyBundle.message("type.expected"));
        }
      }
    }
    marker.done(TYPE_PARAMETER_EXTENDS_BOUND_LIST);
    return TYPE_PARAMETER_EXTENDS_BOUND_LIST;
  }
}
