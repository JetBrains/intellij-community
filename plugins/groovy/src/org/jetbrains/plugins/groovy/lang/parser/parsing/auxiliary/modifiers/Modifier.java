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

package org.jetbrains.plugins.groovy.lang.parser.parsing.auxiliary.modifiers;

import com.intellij.lang.PsiBuilder;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;

/**
 * @autor: Dmitry.Krasilschikov
 * @date: 14.03.2007
 */

/*
 * Modifier ::= private
 *            | public
 *            | protected
 *            | static
 *            | transient
 *            | final
 *            | abstract
 *            | native
 *            | synchronized
 *            | volatile
 *            | srtictfp
 *            | def
 */

public class Modifier implements GroovyElementTypes {
  public static boolean parse(PsiBuilder builder) {
    if (TokenSets.MODIFIERS.contains(builder.getTokenType())) {
      builder.advanceLexer();
      return true;
    }
    return false;
  }
}
