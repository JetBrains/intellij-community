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
package org.jetbrains.plugins.groovy.lang.parser.parsing.statements;

import com.intellij.lang.PsiBuilder;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.parser.parsing.types.TypeSpec;
import org.jetbrains.plugins.groovy.lang.parser.parsing.util.ParserUtils;

/**
 * @author ilyas
 */
public class TupleParse {
  public static boolean parseTupleForAssignment(PsiBuilder builder) {
    PsiBuilder.Marker marker = parseTuple(builder, GroovyElementTypes.REFERENCE_EXPRESSION, false);
    if (marker == null) return false;
    marker.done(GroovyElementTypes.TUPLE_EXPRESSION);
    return true;
  }

  public static boolean parseTupleForVariableDeclaration(PsiBuilder builder) {
    PsiBuilder.Marker marker = parseTuple(builder, GroovyElementTypes.VARIABLE, true);
    if (marker == null) return false;
    marker.drop();
    return true;
  }

  public static PsiBuilder.Marker parseTuple(PsiBuilder builder, IElementType componentType, boolean acceptType) {
    if (builder.getTokenType() != GroovyTokenTypes.mLPAREN) return null;

    final PsiBuilder.Marker marker = builder.mark();
    builder.advanceLexer();
    int count = 0;
    do {
      //skip unnecessary commas
      while (ParserUtils.getToken(builder, GroovyTokenTypes.mCOMMA)) {
        count++;
        builder.error(GroovyBundle.message("identifier.expected"));
      }

      if (acceptType) {
        //parse modifiers for definitions
        PsiBuilder.Marker typeMarker = builder.mark();
        TypeSpec.parse(builder);
        if (builder.getTokenType() != GroovyTokenTypes.mIDENT) {
          typeMarker.rollbackTo();
        }
        else {
          typeMarker.drop();
        }
      }

      PsiBuilder.Marker componentMarker = builder.mark();
      if (!ParserUtils.getToken(builder, GroovyTokenTypes.mIDENT)) {
        builder.error(GroovyBundle.message("identifier.expected"));
        componentMarker.drop();
      }
      else {
        componentMarker.done(componentType);
        count++;
      }
    }
    while (ParserUtils.getToken(builder, GroovyTokenTypes.mCOMMA));

    if (ParserUtils.getToken(builder, GroovyTokenTypes.mRPAREN)) {
      return marker;
    }
    else if (count > 0) {    //accept tuple if there was at least one comma or parsed tuple element inside it
      builder.error(GroovyBundle.message("comma.or.rparen.expected"));
      return marker;
    }
    else {
      marker.rollbackTo();
      return null;
    }
  }
}
