/*
 * Copyright 2003-2018 Dave Griffith
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
package com.siyeh.ig.fixes;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.InspectionGadgetsFix;
import org.jetbrains.annotations.NotNull;

public class DeleteImportFix extends InspectionGadgetsFix {
  @Override
  @NotNull
  public String getFamilyName() {
    return InspectionGadgetsBundle.message("delete.import.quickfix");
  }

  @Override
  public void doFix(Project project, ProblemDescriptor descriptor) {
    final PsiElement importStatement = descriptor.getPsiElement();
    deleteElement(importStatement);
  }
}
