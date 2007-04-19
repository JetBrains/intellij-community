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

package org.jetbrains.plugins.groovy.lang.parser.parsing.toplevel.packaging;

import com.intellij.lang.PsiBuilder;
import com.intellij.lang.PsiBuilder.Marker;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyElementType;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.parser.parsing.util.ParserUtils;


/**
 * @author Ilya.Sergey
 */
public class PackageDefinition implements GroovyElementTypes {

  public static GroovyElementType parse(PsiBuilder builder) {

    // TODO Add annotation parsing

    Marker pMarker = builder.mark();

    if (!ParserUtils.getToken(builder, kPACKAGE, GroovyBundle.message("package.keyword.expected"))) {
      pMarker.drop();
      return WRONGWAY;
    }
    if (ParserUtils.lookAhead(builder, mIDENT)) {
      identifierParse(builder);
    } else {
      builder.error(GroovyBundle.message("identifier.expected"));
    }

    pMarker.done(PACKAGE_DEFINITION);
    return PACKAGE_DEFINITION;
  }

  private static GroovyElementType identifierParse(PsiBuilder builder) {

    Marker marker = builder.mark();
    if (!ParserUtils.getToken(builder, mIDENT, GroovyBundle.message("identifier.expected"))) {
      marker.rollbackTo();
      return WRONGWAY;
    }

    if (mDOT.equals(builder.getTokenType())) {
      Marker newMarker = marker.precede();
      marker.done(IDENTIFIER);
      ParserUtils.getToken(builder, mDOT);
      ParserUtils.getToken(builder, mNLS);
      idSubParse(builder, newMarker);
    } else {
      marker.done(IDENTIFIER);
    }

    return IDENTIFIER;
  }

  private static void idSubParse(PsiBuilder builder, Marker marker) {
    if (!ParserUtils.getToken(builder, mIDENT, GroovyBundle.message("identifier.expected"))) {
      marker.rollbackTo();
      return;
    }
    if (mDOT.equals(builder.getTokenType())) {
      Marker newMarker = marker.precede();
      marker.done(IDENTIFIER);
      ParserUtils.getToken(builder, mDOT);
      ParserUtils.getToken(builder, mNLS);
      idSubParse(builder, newMarker);
    } else {
      marker.done(IDENTIFIER);
    }


  }
}
