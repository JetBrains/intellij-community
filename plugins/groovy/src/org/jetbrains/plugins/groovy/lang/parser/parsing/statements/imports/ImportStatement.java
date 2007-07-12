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

package org.jetbrains.plugins.groovy.lang.parser.parsing.statements.imports;

import com.intellij.lang.PsiBuilder;
import com.intellij.lang.PsiBuilder.Marker;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyElementType;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.parser.parsing.util.ParserUtils;

/**
 * Parses import statement
 *
 * @author ilyas
 */
public class ImportStatement implements GroovyElementTypes {

  public static GroovyElementType parse(PsiBuilder builder) {

    Marker impMarker = builder.mark();

    ParserUtils.getToken(builder, kIMPORT, GroovyBundle.message("import.keyword.expected"));
    ParserUtils.getToken(builder, kSTATIC);
    if (!ImportReference.parse(builder)) {
      builder.error(GroovyBundle.message("import.identifier.expected"));
    }

    impMarker.done(IMPORT_STATEMENT);

    return IMPORT_STATEMENT;
  }

}
