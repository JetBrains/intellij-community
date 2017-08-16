/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.refactoring.convertToStatic;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.codeInspection.type.GroovyStaticTypeCheckVisitor;

import java.util.ArrayList;
import java.util.List;

public class TypeChecker extends GroovyStaticTypeCheckVisitor {
  List<ProblemFix> toApply = new ArrayList<>();

  @Override
  protected void registerError(@NotNull PsiElement location,
                               @NotNull String description,
                               @Nullable LocalQuickFix[] fixes,
                               @NotNull ProblemHighlightType highlightType) {

    if (highlightType == ProblemHighlightType.GENERIC_ERROR) {
      if (fixes != null && fixes.length > 0) {
        final InspectionManager manager = InspectionManager.getInstance(location.getProject());
        final ProblemDescriptor descriptor =
          manager.createProblemDescriptor(location, description, fixes, highlightType, fixes.length == 1, false);
        toApply.add(new ProblemFix(fixes[0], descriptor));
      }
    }
  }

  int applyFixes() {
    for (ProblemFix fix : toApply) {
      fix.apply();
    }
    return toApply.size();
  }

  private static class ProblemFix {
    @NotNull
    LocalQuickFix fix;

    @NotNull
    ProblemDescriptor descriptor;

    public ProblemFix(@NotNull LocalQuickFix fix, @NotNull ProblemDescriptor descriptor) {
      this.fix = fix;
      this.descriptor = descriptor;
    }

    @NotNull
    public LocalQuickFix getFix() {
      return fix;
    }

    @NotNull
    public ProblemDescriptor getDescriptor() {
      return descriptor;
    }

    public void apply() {
      fix.applyFix(descriptor.getPsiElement().getProject(), descriptor);
    }
  }
}
