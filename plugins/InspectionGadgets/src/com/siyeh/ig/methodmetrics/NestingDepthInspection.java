/*
 * Copyright 2003-2005 Dave Griffith
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

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.InspectionGadgetsBundle;
import org.jetbrains.annotations.NotNull;

public class NestingDepthInspection extends MethodMetricInspection {

  public String getID() {
    return "OverlyNestedMethod";
  }

  public String getGroupDisplayName() {
    return GroupNames.METHODMETRICS_GROUP_NAME;
  }

  protected int getDefaultLimit() {
    return 5;
  }

  protected String getConfigurationLabel() {
    return InspectionGadgetsBundle.message("nesting.depth.limit.option");
  }

  public String buildErrorString(PsiElement location) {
    final PsiMethod method = (PsiMethod)location.getParent();
    assert method != null;
    final NestingDepthVisitor visitor = new NestingDepthVisitor();
    method.accept(visitor);
    final int nestingDepth = visitor.getMaximumDepth();
    return InspectionGadgetsBundle.message("nesting.depth.problem.descriptor", nestingDepth);
  }

  public BaseInspectionVisitor buildVisitor() {
    return new NestingDepthMethodVisitor();
  }

  private class NestingDepthMethodVisitor extends BaseInspectionVisitor {

    public void visitMethod(@NotNull PsiMethod method) {
      // note: no call to super
      final NestingDepthVisitor visitor = new NestingDepthVisitor();
      method.accept(visitor);
      final int count = visitor.getMaximumDepth();

      if (count <= getLimit()) {
        return;
      }
      registerMethodError(method);
    }
  }
}
