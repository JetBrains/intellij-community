// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.refactoring.convertToStatic;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.util.InspectionMessage;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.codeInspection.type.GroovyStaticTypeCheckVisitorBase;

import java.util.ArrayList;
import java.util.List;

public class TypeChecker extends GroovyStaticTypeCheckVisitorBase {

  List<ProblemFix> toApply = new ArrayList<>();

  @Override
  protected void registerError(@NotNull PsiElement location,
                               @InspectionMessage @NotNull String description,
                               LocalQuickFix @Nullable [] fixes,
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

  public List<ProblemFix> getFixes() {
    return toApply;
  }

  int applyFixes() {
    for (ProblemFix fix : toApply) {
      fix.apply();
    }
    return toApply.size();
  }

  public static class ProblemFix {
    @NotNull
    LocalQuickFix fix;

    @NotNull
    ProblemDescriptor descriptor;

    ProblemFix(@NotNull LocalQuickFix fix, @NotNull ProblemDescriptor descriptor) {
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
