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
package org.jetbrains.plugins.groovy.lang.psi.patterns;

import com.intellij.patterns.ElementPattern;
import com.intellij.patterns.InitialPatternCondition;
import com.intellij.patterns.PsiFilePattern;
import com.intellij.patterns.PsiJavaPatterns;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;

public class GroovyPatterns extends PsiJavaPatterns {

  public static GroovyElementPattern groovyElement() {
    return new GroovyElementPattern.Capture<GroovyPsiElement>(GroovyPsiElement.class);
  }

  public static GroovyBinaryExpressionPattern groovyBinaryExpression() {
    return new GroovyBinaryExpressionPattern();
  }

  public static GroovyElementPattern.Capture<GrLiteral> groovyLiteralExpression() {
    return groovyLiteralExpression(null);
  }

  public static GroovyElementPattern.Capture<GrLiteral> groovyLiteralExpression(final ElementPattern value) {
    return new GroovyElementPattern.Capture<GrLiteral>(new InitialPatternCondition<GrLiteral>(GrLiteral.class) {
      public boolean accepts(@Nullable final Object o, final ProcessingContext context) {
        return o instanceof GrLiteral
               && (value == null || value.accepts(((GrLiteral)o).getValue(), context));
      }
    });
  }

  public static PsiFilePattern.Capture<GroovyFile> groovyScript() {
    return new PsiFilePattern.Capture<GroovyFile>(new InitialPatternCondition<GroovyFile>(GroovyFile.class) {
      @Override
      public boolean accepts(@Nullable Object o, ProcessingContext context) {
        return o instanceof GroovyFileBase && ((GroovyFileBase)o).isScript();
      }
    });
  }
}
