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

package org.jetbrains.plugins.groovy.lang.parser.parsing.auxiliary;

import com.intellij.lang.PsiBuilder;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.typeDefinitions.ReferenceElement;
import org.jetbrains.plugins.groovy.lang.parser.parsing.util.ParserUtils;

/**
 * @author Dmitry.Krasilschikov
 * @date 26.03.2007
 */
public class ThrowClause {
  public static void parse(PsiBuilder builder) {
    PsiBuilder.Marker throwClauseMarker = builder.mark();

    if (!ParserUtils.getToken(builder, GroovyTokenTypes.kTHROWS)) {
      throwClauseMarker.done(GroovyElementTypes.THROW_CLAUSE);
      return;
    }

    ParserUtils.getToken(builder, GroovyTokenTypes.mNLS);

    if (ReferenceElement.parse(builder, false, true, true, false, false) == ReferenceElement.ReferenceElementResult.FAIL) {
      builder.error(GroovyBundle.message("identifier.expected"));
      throwClauseMarker.done(GroovyElementTypes.THROW_CLAUSE);
      return;
    }

    while (ParserUtils.getToken(builder, GroovyTokenTypes.mCOMMA)) {
      ParserUtils.getToken(builder, GroovyTokenTypes.mNLS);

      if (ReferenceElement.parse(builder, false, true, true, false, false) == ReferenceElement.ReferenceElementResult.FAIL) {
        break;
      }
    }

    throwClauseMarker.done(GroovyElementTypes.THROW_CLAUSE);
  }
}
