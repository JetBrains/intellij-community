/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.parser.parsing.util.ParserUtils;

import static org.jetbrains.plugins.groovy.lang.parser.parsing.statements.typeDefinitions.ReferenceElement.ReferenceElementResult.FAIL;

/**
 * @author: Dmitry.Krasilschikov
 * @date: 28.03.2007
 */
public class TypeArguments implements GroovyElementTypes {
  public static boolean parseTypeArguments(PsiBuilder builder, boolean expressionPossible) {
    return parseTypeArguments(builder, expressionPossible, false);
  }
  
  public static boolean parseTypeArguments(PsiBuilder builder, boolean expressionPossible, boolean allowDiamond) {
    PsiBuilder.Marker marker = builder.mark();

    if (!ParserUtils.getToken(builder, mLT)) {
      marker.rollbackTo();
      return false;
    }

    ParserUtils.getToken(builder, mNLS);

    if (allowDiamond && ParserUtils.getToken(builder, mGT)) {
      marker.done(TYPE_ARGUMENTS);
      return true;
    }

    if (!parseArgument(builder)) {
      builder.error(GroovyBundle.message("type.argument.expected"));
      if (ParserUtils.getToken(builder, mGT)) {
        marker.done(TYPE_ARGUMENTS);
        return true;
      }
      else {
        marker.rollbackTo();
        return false;
      }
    }

    boolean hasComma = ParserUtils.lookAhead(builder, mCOMMA);
    while (ParserUtils.getToken(builder, mCOMMA)) {
      ParserUtils.getToken(builder, mNLS);

      if (!parseArgument(builder)) {
        builder.error("type.argument.expected");
      }
    }

    PsiBuilder.Marker rb = builder.mark();
    ParserUtils.getToken(builder, mNLS);

    if (ParserUtils.getToken(builder, mGT)) {
      rb.drop();
    }
    else if (hasComma) {
      rb.rollbackTo();
      builder.error(GroovyBundle.message("gt.expected"));
    }
    else {
      rb.drop();
      if (expressionPossible) {
        marker.rollbackTo();
        return false;
      }
      else {
        builder.error(GroovyBundle.message("gt.expected"));
      }
    }

    marker.done(TYPE_ARGUMENTS);
    return true;
  }

  private static boolean parseArgument(PsiBuilder builder) {
    if (builder.getTokenType() == mQUESTION) {
      //wildcard
      PsiBuilder.Marker taMarker = builder.mark();
      ParserUtils.getToken(builder, mQUESTION);
      if (ParserUtils.getToken(builder, kSUPER) || ParserUtils.getToken(builder, kEXTENDS)) {
        ParserUtils.getToken(builder, mNLS);
        TypeSpec.parse(builder, false, false);
        ParserUtils.getToken(builder, mNLS);
      }

      taMarker.done(TYPE_ARGUMENT);
      return true;
    }

    return TypeSpec.parse(builder, false, false) != FAIL;
  }
}
