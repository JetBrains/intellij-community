// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.psi.patterns;

import com.intellij.patterns.PatternCondition;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotation;

public class GrAnnotationPattern extends GroovyElementPattern<GrAnnotation, GrAnnotationPattern> {
  public GrAnnotationPattern() {
    super(GrAnnotation.class);
  }

  public static @NotNull GrAnnotationPattern annotation() {
    return new GrAnnotationPattern();
  }

  public @NotNull GrAnnotationPattern withQualifiedName(final @NotNull String qname) {
    return with(new PatternCondition<>("withQualifiedName") {
      @Override
      public boolean accepts(@NotNull GrAnnotation annotation, ProcessingContext context) {
        return qname.equals(annotation.getQualifiedName());
      }
    });
  }
}
