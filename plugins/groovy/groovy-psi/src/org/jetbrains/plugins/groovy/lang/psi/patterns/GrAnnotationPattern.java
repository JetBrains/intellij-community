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
package org.jetbrains.plugins.groovy.lang.psi.patterns;

import com.intellij.patterns.PatternCondition;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotation;

public class GrAnnotationPattern extends GroovyElementPattern<GrAnnotation, GrAnnotationPattern> {
  public GrAnnotationPattern() {
    super(GrAnnotation.class);
  }

  @NotNull
  public static GrAnnotationPattern annotation() {
    return new GrAnnotationPattern();
  }

  @NotNull
  public GrAnnotationPattern withQualifiedName(@NotNull final String qname) {
    return with(new PatternCondition<GrAnnotation>("withQualifiedName") {
      @Override
      public boolean accepts(@NotNull GrAnnotation annotation, ProcessingContext context) {
        return qname.equals(annotation.getQualifiedName());
      }
    });
  }
}
