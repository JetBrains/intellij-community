/*
 * Copyright 2007-2008 Dave Griffith
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
package org.jetbrains.plugins.groovy.codeInspection.confusing;

import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspection;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspectionVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;

public class GroovyOctalIntegerInspection extends BaseInspection {

  @Override
  @Nls
  @NotNull
  public String getDisplayName() {
    return "Octal integer";
  }

  @Override
  @Nullable
  protected String buildErrorString(Object... args) {
    return "Octal integer #ref #loc";
  }

  @NotNull
  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new BaseInspectionVisitor() {
      @Override
      public void visitLiteralExpression(@NotNull GrLiteral literal) {
        super.visitLiteralExpression(literal);
        @NonNls final String text = literal.getText();
        if (!text.startsWith("0")) return;

        if (text.replaceAll("0", "").isEmpty()) return;
        if ("0g".equals(text) || "0G".equals(text)) return;
        if ("0i".equals(text) || "0I".equals(text)) return;
        if ("0l".equals(text) || "0L".equals(text)) return;

        if (text.startsWith("0x") || text.startsWith("0X")) return;
        if (text.startsWith("0b") || text.startsWith("0B")) return;

        if (text.endsWith("d") || text.endsWith("D")) return;
        if (text.endsWith("f") || text.endsWith("F")) return;

        if (text.contains(".") || text.contains("e") || text.contains("E")) return;
        
        registerError(literal);
      }
    };
  }
}