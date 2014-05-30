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
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.typeDefinitions.ReferenceElement;
import org.jetbrains.plugins.groovy.lang.parser.parsing.util.ParserUtils;

/**
 * @author: Dmitry.Krasilschikov
 * @date: 28.03.2007
 */
public class TypeArguments {
  public static boolean parseTypeArguments(PsiBuilder builder, boolean expressionPossible) {
    return parseTypeArguments(builder, expressionPossible, false);
  }
  
  public static boolean parseTypeArguments(PsiBuilder builder, boolean expressionPossible, boolean allowDiamond) {
    PsiBuilder.Marker marker = builder.mark();

    if (!ParserUtils.getToken(builder, GroovyTokenTypes.mLT)) {
      marker.rollbackTo();
      return false;
    }

    ParserUtils.getToken(builder, GroovyTokenTypes.mNLS);

    if (allowDiamond && ParserUtils.getToken(builder, GroovyTokenTypes.mGT)) {
      marker.done(GroovyElementTypes.TYPE_ARGUMENTS);
      return true;
    }

    if (!parseArgument(builder)) {
      builder.error(GroovyBundle.message("type.argument.expected"));
      if (ParserUtils.getToken(builder, GroovyTokenTypes.mGT)) {
        marker.done(GroovyElementTypes.TYPE_ARGUMENTS);
        return true;
      }
      else {
        marker.rollbackTo();
        return false;
      }
    }

    boolean hasComma = ParserUtils.lookAhead(builder, GroovyTokenTypes.mCOMMA);
    while (ParserUtils.getToken(builder, GroovyTokenTypes.mCOMMA)) {
      ParserUtils.getToken(builder, GroovyTokenTypes.mNLS);

      if (!parseArgument(builder)) {
        builder.error("type.argument.expected");
      }
    }

    PsiBuilder.Marker rb = builder.mark();
    ParserUtils.getToken(builder, GroovyTokenTypes.mNLS);

    if (ParserUtils.getToken(builder, GroovyTokenTypes.mGT)) {
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

    marker.done(GroovyElementTypes.TYPE_ARGUMENTS);
    return true;
  }

  private static boolean parseArgument(PsiBuilder builder) {
    if (builder.getTokenType() == GroovyTokenTypes.mQUESTION) {
      //wildcard
      PsiBuilder.Marker taMarker = builder.mark();
      ParserUtils.getToken(builder, GroovyTokenTypes.mQUESTION);
      if (ParserUtils.getToken(builder, GroovyTokenTypes.kSUPER) || ParserUtils.getToken(builder, GroovyTokenTypes.kEXTENDS)) {
        ParserUtils.getToken(builder, GroovyTokenTypes.mNLS);
        TypeSpec.parse(builder, false, false);
        ParserUtils.getToken(builder, GroovyTokenTypes.mNLS);
      }

      taMarker.done(GroovyElementTypes.TYPE_ARGUMENT);
      return true;
    }

    return TypeSpec.parse(builder, false, false) != ReferenceElement.ReferenceElementResult.FAIL;
  }
}
