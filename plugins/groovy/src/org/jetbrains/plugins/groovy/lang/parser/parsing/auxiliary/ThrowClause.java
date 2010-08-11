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

package org.jetbrains.plugins.groovy.lang.parser.parsing.auxiliary;

import com.intellij.lang.PsiBuilder;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.typeDefinitions.ReferenceElement;
import org.jetbrains.plugins.groovy.lang.parser.parsing.util.ParserUtils;

import static org.jetbrains.plugins.groovy.lang.parser.parsing.statements.typeDefinitions.ReferenceElement.ReferenceElementResult.fail;

/**
 * @author Dmitry.Krasilschikov
 * @date 26.03.2007
 */
public class ThrowClause implements GroovyElementTypes {
  public static void parse(PsiBuilder builder) {
    PsiBuilder.Marker throwClauseMarker = builder.mark();

    if (!ParserUtils.getToken(builder, kTHROWS)) {
      throwClauseMarker.done(THROW_CLAUSE);
      return;
    }

    ParserUtils.getToken(builder, mNLS);

    if (ReferenceElement.parseReferenceElement(builder) == fail) {
      throwClauseMarker.done(THROW_CLAUSE);
      builder.error(GroovyBundle.message("identifier.expected"));
      return;
    }

    while (ParserUtils.getToken(builder, mCOMMA)) {
      ParserUtils.getToken(builder, mNLS);

      if (ReferenceElement.parseReferenceElement(builder) == fail) {
        throwClauseMarker.done(THROW_CLAUSE);
        return;
      }
    }

    throwClauseMarker.done(THROW_CLAUSE);
  }
}
