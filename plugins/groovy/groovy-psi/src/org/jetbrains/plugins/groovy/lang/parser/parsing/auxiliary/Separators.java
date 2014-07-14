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
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.parser.parsing.util.ParserUtils;

/**
 * Parse separators
 *
 * @author ilyas
 */
public class Separators {

  public static boolean parse(PsiBuilder builder) {
    if (GroovyTokenTypes.mSEMI.equals(builder.getTokenType()) || GroovyTokenTypes.mNLS.equals(builder.getTokenType())) {
      builder.advanceLexer();
      while (ParserUtils.getToken(builder, GroovyTokenTypes.mNLS) || ParserUtils.getToken(builder, GroovyTokenTypes.mSEMI)) {
        // Parse newLines
      }
      return true;
    }

    return false;
  }

}
