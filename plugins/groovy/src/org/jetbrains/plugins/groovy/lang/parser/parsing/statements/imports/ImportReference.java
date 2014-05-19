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

package org.jetbrains.plugins.groovy.lang.parser.parsing.statements.imports;

import com.intellij.lang.PsiBuilder;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.typeDefinitions.ReferenceElement;
import org.jetbrains.plugins.groovy.lang.parser.parsing.util.ParserUtils;

/**
 * Import identifier
 *
 * @author ilyas
 */
public class ImportReference {

  public static boolean parse(PsiBuilder builder) {

    if (!TokenSets.CODE_REFERENCE_ELEMENT_NAME_TOKENS.contains(builder.getTokenType())) {
      return false;
    }

    if (ReferenceElement.parseForImport(builder) == ReferenceElement.ReferenceElementResult.FAIL) {
      return false;
    }

    if (ParserUtils.getToken(builder, GroovyTokenTypes.mDOT)) {
      ParserUtils.getToken(builder, GroovyTokenTypes.mNLS);
      if (!ParserUtils.getToken(builder, GroovyTokenTypes.mSTAR)) {
        builder.error(GroovyBundle.message("identifier.expected"));
      }
    }

    if (ParserUtils.getToken(builder, GroovyTokenTypes.kAS)) {
      ParserUtils.getToken(builder, GroovyTokenTypes.mNLS);
      if (!ParserUtils.getToken(builder, GroovyTokenTypes.mIDENT)) {
        builder.error(GroovyBundle.message("identifier.expected"));
      }
    }

    return true;
  }
}
