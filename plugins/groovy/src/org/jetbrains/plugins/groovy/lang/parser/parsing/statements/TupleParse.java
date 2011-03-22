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
package org.jetbrains.plugins.groovy.lang.parser.parsing.statements;

import com.intellij.lang.PsiBuilder;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.lang.parser.parsing.types.TypeSpec;
import org.jetbrains.plugins.groovy.lang.parser.parsing.util.ParserUtils;

import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.*;

/**
 * @author ilyas
 */
public class TupleParse {
  public static boolean parseTuple(PsiBuilder builder, IElementType tupleType, IElementType componentType) {
    if (builder.getTokenType() != mLPAREN) return false;

    final PsiBuilder.Marker marker = builder.mark();
    builder.advanceLexer();
    do {
      //skip unnecessary commas
      while (ParserUtils.getToken(builder, mCOMMA)) {
        builder.error(GroovyBundle.message("identifier.expected"));
      }

      //parse modifiers for definitions
      PsiBuilder.Marker typeMarker = builder.mark();
      TypeSpec.parse(builder);
      if (builder.getTokenType() != mIDENT) {
        typeMarker.rollbackTo();
      }
      else {
        typeMarker.drop();
      }
      PsiBuilder.Marker varMarker = builder.mark();
      if (!ParserUtils.getToken(builder, mIDENT)) {
        builder.error(GroovyBundle.message("identifier.expected"));
        varMarker.drop();
      } else {
        varMarker.done(componentType);
      }
    } while (ParserUtils.getToken(builder, mCOMMA));

    if (ParserUtils.getToken(builder, mRPAREN)) {
      marker.done(tupleType);
      return true;
    } else {
      marker.rollbackTo();
      return false;
    }
  }
}
