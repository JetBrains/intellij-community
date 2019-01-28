// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.parser.parsing.toplevel;

import com.intellij.lang.PsiBuilder;
import org.jetbrains.plugins.groovy.lang.parser.GroovyParser;
import org.jetbrains.plugins.groovy.lang.parser.GroovyParserDefinition;

@Deprecated
public class CompilationUnit {

  @Deprecated
  public static void parseFile(PsiBuilder builder, GroovyParser parser) {
    parser.parseLight(GroovyParserDefinition.GROOVY_FILE, builder);
  }
}
