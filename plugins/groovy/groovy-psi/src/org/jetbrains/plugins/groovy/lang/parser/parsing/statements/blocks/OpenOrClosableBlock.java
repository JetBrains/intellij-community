// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.parser.parsing.statements.blocks;

import com.intellij.lang.PsiBuilder;
import org.jetbrains.plugins.groovy.lang.parser.GroovyParser;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes;

@Deprecated
public final class OpenOrClosableBlock {

  @Deprecated
  public static void parseOpenBlockDeep(PsiBuilder builder, GroovyParser parser) {
    parser.parseLight(GroovyElementTypes.OPEN_BLOCK, builder);
  }

  @Deprecated
  public static void parseClosableBlockDeep(PsiBuilder builder, GroovyParser parser) {
    parser.parseLight(GroovyElementTypes.CLOSURE, builder);
  }
}
