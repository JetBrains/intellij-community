/*
 * Copyright 2003-2012 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.portability;

import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiType;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.NotNull;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HardcodedLineSeparatorsInspection extends BaseInspection {

  private static final Pattern newlines = Pattern.compile("\\\\n|\\\\r|\\\\0{0,1}12|\\\\0{0,1}15");

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("hardcoded.line.separator.display.name");
  }

  @Override
  @NotNull
  public String getID() {
    return "HardcodedLineSeparator";
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("hardcoded.line.separator.problem.descriptor");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new HardcodedLineSeparatorsVisitor();
  }

  private static class HardcodedLineSeparatorsVisitor extends BaseInspectionVisitor {

    @Override
    public void visitLiteralExpression(@NotNull PsiLiteralExpression expression) {
      super.visitLiteralExpression(expression);
      final PsiType type = expression.getType();
      if (type == null || !TypeUtils.isJavaLangString(type) && !type.equals(PsiType.CHAR)) {
        return;
      }
      final String text = expression.getText();
      final Matcher matcher = newlines.matcher(text);
      int end = 0;
      while (matcher.find(end)) {
        final int start = matcher.start();
        end = matcher.end();
        registerErrorAtOffset(expression, start, end - start);
      }
    }
  }
}
