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
import com.intellij.lang.PsiBuilder.Marker;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.parser.GroovyParser;
import org.jetbrains.plugins.groovy.lang.parser.parsing.auxiliary.modifiers.Modifiers;
import org.jetbrains.plugins.groovy.lang.parser.parsing.util.ParserUtils;

/**
 * Parses import statement
 *
 * @author ilyas
 */
public class ImportStatement {

  public static boolean parse(PsiBuilder builder, GroovyParser parser) {
    Marker impMarker = builder.mark();

    Modifiers.parse(builder, parser);

    if (!GroovyTokenTypes.kIMPORT.equals(builder.getTokenType())) {
      impMarker.rollbackTo();
      return false;
    }

    parseAfterModifiers(builder);
    impMarker.done(GroovyElementTypes.IMPORT_STATEMENT);
    return true;
  }

  public static void parseAfterModifiers(PsiBuilder builder) {
    ParserUtils.getToken(builder, GroovyTokenTypes.kIMPORT, GroovyBundle.message("import.keyword.expected"));
    ParserUtils.getToken(builder, GroovyTokenTypes.kSTATIC);
    if (!ImportReference.parse(builder)) {
      builder.error(GroovyBundle.message("import.identifier.expected"));
    }

  }
}
