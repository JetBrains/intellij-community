/*
 *  Copyright 2000-2007 JetBrains s.r.o.
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package org.jetbrains.plugins.groovy.lang.parser.parsing.statements.imports;

import com.intellij.lang.PsiBuilder;
import com.intellij.lang.PsiBuilder.Marker;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyElementType;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.parser.parsing.util.ParserUtils;

/**
 * Import identifier
 *
 * @author Ilya Sergey
 */
public class IdentifierReference implements GroovyElementTypes {

  public static GroovyElementType parse(PsiBuilder builder) {

    Marker idMarker = builder.mark();

    if (ParserUtils.getToken(builder, mIDENT, GroovyBundle.message("identifier.expected"))) {
      if (ParserUtils.lookAhead(builder, mDOT)) {
        Marker newMarker = idMarker.precede();
        idMarker.done(IMPORT_REFERENCE);
        subParse(builder, newMarker);
      } else if (ParserUtils.lookAhead(builder, kAS)) {
        builder.advanceLexer();
        if (ParserUtils.lookAhead(builder, mNLS, mIDENT)) {
          ParserUtils.getToken(builder, mNLS);
        }
        ParserUtils.getToken(builder, mIDENT, GroovyBundle.message("identifier.expected"));
        idMarker.done(IMPORT_SELECTOR);
      } else {
        idMarker.done(IMPORT_REFERENCE);
      }
    } else {
      idMarker.drop();
    }
    return IMPORT_REFERENCE;
  }

  private static void subParse(PsiBuilder builder, Marker marker) {
    ParserUtils.getToken(builder, mDOT);
    if (ParserUtils.lookAhead(builder, mIDENT, mDOT) ||
            ParserUtils.lookAhead(builder, mNLS, mIDENT, mDOT)) {
      ParserUtils.getToken(builder, mNLS);
      builder.advanceLexer();
      Marker newMarker = marker.precede();
      marker.done(IMPORT_REFERENCE);
      subParse(builder, newMarker);
    } else if (ParserUtils.lookAhead(builder, mSTAR) ||
            ParserUtils.lookAhead(builder, mNLS, mSTAR)) {
      ParserUtils.getToken(builder, mNLS);
      builder.advanceLexer();
      marker.done(IMPORT_REFERENCE);
    } else if (ParserUtils.lookAhead(builder, mIDENT, kAS) ||
            ParserUtils.lookAhead(builder, mNLS, mIDENT, kAS)) {
      marker.drop();
      ParserUtils.getToken(builder, mNLS);
      Marker selMarker = builder.mark();
      builder.advanceLexer();
      builder.getTokenText(); // eat identifier and pick lexer
      builder.advanceLexer(); // as
      if (ParserUtils.lookAhead(builder, mNLS, mIDENT)) {
        ParserUtils.getToken(builder, mNLS);
      }
      ParserUtils.getToken(builder, mIDENT, GroovyBundle.message("identifier.expected"));
      selMarker.done(IMPORT_SELECTOR);
    } else if (ParserUtils.lookAhead(builder, mIDENT) ||
            ParserUtils.lookAhead(builder, mNLS, mIDENT)) {
      ParserUtils.getToken(builder, mNLS);
      ParserUtils.getToken(builder, mIDENT, GroovyBundle.message("identifier.expected"));
      marker.done(IMPORT_REFERENCE);
    } else {
      builder.error(GroovyBundle.message("identifier.expected"));
      marker.done(IMPORT_REFERENCE);
    }
  }

}
