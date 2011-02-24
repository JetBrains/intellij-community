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

package org.jetbrains.plugins.groovy.lang.parser.parsing.toplevel;

import com.intellij.lang.PsiBuilder;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.parser.GroovyParser;
import org.jetbrains.plugins.groovy.lang.parser.parsing.auxiliary.Separators;
import org.jetbrains.plugins.groovy.lang.parser.parsing.toplevel.packaging.PackageDefinition;
import org.jetbrains.plugins.groovy.lang.parser.parsing.util.ParserUtils;

/**
 * Main node of any Groovy script
 *
 * @autor: Dmitry.Krasilschikov, ilyas
 */
public class CompilationUnit implements GroovyElementTypes {

  public static void parseFile(PsiBuilder builder, GroovyParser parser) {

    ParserUtils.getToken(builder, mSH_COMMENT);
    ParserUtils.getToken(builder, mNLS);

    if (!PackageDefinition.parse(builder, parser)) {
      parser.parseStatementWithImports(builder);
    }
    cleanAfterError(builder);

    while (Separators.parse(builder)) {
      parser.parseStatementWithImports(builder);
      cleanAfterError(builder);
    }
  }

  /**
   * Marks some trash after statement parsing as error
   *
   * @param builder PsiBuilder
   */
  private static void cleanAfterError(PsiBuilder builder) {
    int i = 0;
    PsiBuilder.Marker em = builder.mark();
    while (!builder.eof() &&
            !(mNLS.equals(builder.getTokenType()) ||
                    mSEMI.equals(builder.getTokenType()))
            ) {
      builder.advanceLexer();
      i++;
    }
    if (i > 0) {
      em.error(GroovyBundle.message("separator.expected"));
    } else {
      em.drop();
    }
  }

}
