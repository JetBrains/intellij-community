/*
 * Copyright 2003-2018 Dave Griffith, Bas Leijdekkers
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

public class RemoveModifierFix extends InspectionGadgetsFix {

  private final String modifierText;

  public RemoveModifierFix(String modifierText) {
    this.modifierText = modifierText;
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return "Remove modifier";
  }

  @Override
  @NotNull
  public String getName() {
    return InspectionGadgetsBundle.message("remove.modifier.quickfix",
                                           modifierText);
  }

  @Override
  public void doFix(Project project, ProblemDescriptor descriptor) {
    final PsiElement modifierElement = descriptor.getPsiElement();
    deleteElement(modifierElement);
  }
}