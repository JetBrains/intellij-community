/*
 * Copyright 2003-2014 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.methodmetrics;

import com.intellij.psi.PsiMethod;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.classmetrics.CyclomaticComplexityVisitor;
import org.jetbrains.annotations.NotNull;

public class CyclomaticComplexityInspection extends MethodMetricInspection {

  @Override
  @NotNull
  public String getID() {
    return "OverlyComplexMethod";
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "cyclomatic.complexity.display.name");
  }

  @Override
  protected int getDefaultLimit() {
    return 10;
  }

  @Override
  protected String getConfigurationLabel() {
    return InspectionGadgetsBundle.message(
      "method.complexity.limit.option");
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    final Integer complexity = (Integer)infos[0];
    return InspectionGadgetsBundle.message(
      "cyclomatic.complexity.problem.descriptor", complexity);
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new MethodComplexityVisitor();
  }

  private class MethodComplexityVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethod(@NotNull PsiMethod method) {
      // note: no call to super
      if (method.getNameIdentifier() == null) {
        return;
      }
      final CyclomaticComplexityVisitor visitor =
        new CyclomaticComplexityVisitor();
      method.accept(visitor);
      final int complexity = visitor.getComplexity();
      if (complexity <= getLimit()) {
        return;
      }
      registerMethodError(method, Integer.valueOf(complexity));
    }
  }
}