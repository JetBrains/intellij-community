/*
 * Copyright 2006 Sascha Weinreuter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.intellij.plugins.intelliLang.util;

import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.util.IncorrectOperationException;
import org.intellij.plugins.intelliLang.inject.java.validation.InjectionNotApplicable;
import org.jetbrains.annotations.NotNull;

public class RemoveAnnotationFix implements LocalQuickFix {
  private final LocalInspectionTool myTool;

  public RemoveAnnotationFix(LocalInspectionTool tool) {
    myTool = tool;
  }

  @NotNull
  public String getName() {
    return "Remove Annotation";
  }

  @NotNull
  public String getFamilyName() {
    return myTool.getGroupDisplayName();
  }

  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    if (FileModificationService.getInstance().preparePsiElementForWrite(descriptor.getPsiElement())) {
      try {
        descriptor.getPsiElement().delete();
      }
      catch (IncorrectOperationException e) {
        Logger.getInstance(InjectionNotApplicable.class.getName()).error(e);
      }
    }
  }
}
