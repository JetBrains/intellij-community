/*
 * Copyright 2003-2007 Dave Griffith, Bas Leijdekkers
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
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import org.jetbrains.annotations.NotNull;

public class MethodWithMultipleLoopsInspection extends BaseInspection {

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "method.with.multiple.loops.display.name");
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    final Integer negationCount = (Integer)infos[0];
    return InspectionGadgetsBundle.message(
      "method.with.multiple.loops.problem.descriptor", negationCount);
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new MethodWithMultipleLoopsVisitor();
  }

  private static class MethodWithMultipleLoopsVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitMethod(@NotNull PsiMethod method) {
      // note: no call to super
      if (method.getNameIdentifier() == null) {
        return;
      }
      final LoopCountVisitor visitor = new LoopCountVisitor();
      method.accept(visitor);
      final int negationCount = visitor.getCount();
      if (negationCount <= 1) {
        return;
      }
      registerMethodError(method, Integer.valueOf(negationCount));
    }
  }
}