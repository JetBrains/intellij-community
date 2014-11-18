/*
 * Copyright 2006-2008 Bas Leijdekkers
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
package com.siyeh.ig.visibility;

import com.intellij.psi.PsiField;
import com.intellij.psi.PsiParameter;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import org.jetbrains.annotations.NotNull;

public class AnonymousClassVariableHidesContainingMethodVariableInspectionBase extends BaseInspection {

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "anonymous.class.variable.hides.containing.method.variable.display.name");
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    final Object info = infos[0];
    if (info instanceof PsiParameter) {
      return InspectionGadgetsBundle.message(
        "anonymous.class.parameter.hides.containing.method.variable.problem.descriptor");
    }
    else if (info instanceof PsiField) {
      return InspectionGadgetsBundle.message(
        "anonymous.class.field.hides.containing.method.variable.problem.descriptor");
    }
    return InspectionGadgetsBundle.message(
      "anonymous.class.variable.hides.containing.method.variable.problem.descriptor");
  }

  @Override
  protected boolean buildQuickFixesOnlyForOnTheFlyErrors() {
    return true;
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new AnonymousClassVariableHidesOuterClassVariableVisitor();
  }
}