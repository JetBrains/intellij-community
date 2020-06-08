// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.parser.parsing.statements.constructor;

import com.intellij.lang.PsiBuilder;
import org.jetbrains.plugins.groovy.lang.parser.GroovyParser;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes;

@Deprecated
public final class ConstructorBody {

  @Deprecated
  public static void parseConstructorBodyDeep(PsiBuilder builder, GroovyParser parser) {
    parser.parseLight(GroovyElementTypes.CONSTRUCTOR_BLOCK, builder);
  }
}
