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

package org.jetbrains.plugins.groovy.lang.parser.parsing.toplevel;

import com.intellij.lang.PsiBuilder;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.parser.GroovyParser;
import org.jetbrains.plugins.groovy.lang.parser.parsing.auxiliary.Separators;
import org.jetbrains.plugins.groovy.lang.parser.parsing.toplevel.packaging.PackageDefinition;
import org.jetbrains.plugins.groovy.lang.parser.parsing.util.ParserUtils;

/**
 * Main node of any Groovy script
 *
 * @autor: Dmitry.Krasilschikov, ilyas
 */
public class CompilationUnit {

  public static void parseFile(PsiBuilder builder, GroovyParser parser) {

    ParserUtils.getToken(builder, GroovyTokenTypes.mSH_COMMENT);
    ParserUtils.getToken(builder, GroovyTokenTypes.mNLS);

    if (!PackageDefinition.parse(builder, parser)) {
      parser.parseStatementWithImports(builder);
    }

    while (!builder.eof()) {
      if (!Separators.parse(builder)) {
        builder.error(GroovyBundle.message("separator.expected"));
      }
      if (builder.eof()) break;
      if (!parser.parseStatementWithImports(builder)) {
        ParserUtils.wrapError(builder, GroovyBundle.message("unexpected.symbol"));
      }
    }
  }
}
