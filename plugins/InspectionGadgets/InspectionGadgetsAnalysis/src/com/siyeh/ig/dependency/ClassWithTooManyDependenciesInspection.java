/*
 * Copyright 2006-2011 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.dependency;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInspection.CommonProblemDescriptor;
import com.intellij.codeInspection.GlobalInspectionContext;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemDescriptionsProcessor;
import com.intellij.codeInspection.reference.RefClass;
import com.intellij.codeInspection.reference.RefJavaVisitor;
import com.intellij.codeInspection.reference.RefManager;
import com.intellij.codeInspection.ui.SingleIntegerFieldOptionsPanel;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseGlobalInspection;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Set;

public class ClassWithTooManyDependenciesInspection
  extends BaseGlobalInspection {

  @SuppressWarnings({"PublicField"})
  public int limit = 10;

  @NotNull
  @Override
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "class.with.too.many.dependencies.display.name");
  }

  @Override
  public void runInspection(@NotNull AnalysisScope scope,
                            @NotNull final InspectionManager inspectionManager,
                            @NotNull GlobalInspectionContext globalInspectionContext,
                            @NotNull final ProblemDescriptionsProcessor problemDescriptionsProcessor) {
    final RefManager refManager = globalInspectionContext.getRefManager();
    refManager.iterate(new RefJavaVisitor() {

      @Override
      public void visitClass(@NotNull RefClass refClass) {
        super.visitClass(refClass);
        if (!globalInspectionContext.shouldCheck(refClass, ClassWithTooManyDependenciesInspection.this)) return;
        if (refClass.getOwner() instanceof RefClass) {
          return;
        }
        final Set<RefClass> dependencies =
          DependencyUtils.calculateDependenciesForClass(refClass);
        final int numDependencies = dependencies.size();
        if (numDependencies <= limit) {
          return;
        }
        final String errorString = InspectionGadgetsBundle.message(
          "class.with.too.many.dependencies.problem.descriptor",
          refClass.getName(), numDependencies, limit);
        final CommonProblemDescriptor[] descriptors = {
          inspectionManager.createProblemDescriptor(errorString)};
        problemDescriptionsProcessor.addProblemElement(refClass, descriptors);
      }
    });
  }

  @Override
  public JComponent createOptionsPanel() {
    return new SingleIntegerFieldOptionsPanel(
      InspectionGadgetsBundle.message(
        "class.with.too.many.dependencies.max.option"),
      this, "limit");
  }
}
