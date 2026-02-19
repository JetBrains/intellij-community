// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.psi.patterns;

import com.intellij.patterns.InitialPatternCondition;
import com.intellij.patterns.PsiMemberPattern;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;

public class GroovyFieldPattern extends PsiMemberPattern<GrField, GroovyFieldPattern> {

  public GroovyFieldPattern() {
    super(new InitialPatternCondition<>(GrField.class) {
      @Override
      public boolean accepts(final @Nullable Object o, final ProcessingContext context) {
        return o instanceof GrField;
      }
    });
  }
}
