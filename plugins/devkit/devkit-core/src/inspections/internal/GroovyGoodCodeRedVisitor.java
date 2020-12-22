// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.inspections.internal;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.util.InspectionMessage;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.codeInspection.bugs.GrAccessibilityChecker;
import org.jetbrains.plugins.groovy.codeInspection.type.GroovyStaticTypeCheckVisitorBase;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;

public class GroovyGoodCodeRedVisitor implements GoodCodeRedVisitor {

  @NotNull
  @Override
  public PsiElementVisitor createVisitor(ProblemsHolder holder) {
    if (!Registry.is("groovy.good.code.is.red", false)) {
      return PsiElementVisitor.EMPTY_VISITOR;
    }
    GroovyFileBase file = (GroovyFileBase)holder.getFile();
    Project project = holder.getProject();
    GrAccessibilityChecker accessibilityChecker = new GrAccessibilityChecker(file, project);
    GroovyElementVisitor typeCheckVisitor = new GroovyStaticTypeCheckVisitorBase() {
      @Override
      protected void registerError(@NotNull PsiElement location,
                                   @NotNull @InspectionMessage String description,
                                   LocalQuickFix @Nullable [] fixes,
                                   @NotNull ProblemHighlightType highlightType) {
        if (highlightType == ProblemHighlightType.GENERIC_ERROR) {
          holder.registerProblem(location, description);
        }
      }
    };

    return new GroovyPsiElementVisitor(new GroovyElementVisitor() {
      @Override
      public void visitElement(@NotNull GroovyPsiElement element) {
        super.visitElement(element);
        element.accept(typeCheckVisitor);
      }

      @Override
      public void visitReferenceExpression(@NotNull GrReferenceExpression referenceExpression) {
        super.visitReferenceExpression(referenceExpression);
        HighlightInfo info = accessibilityChecker.checkReferenceExpression(referenceExpression);
        if (info != null) {
          registerProblem(holder, info, referenceExpression);
        }
      }

      @Override
      public void visitCodeReferenceElement(@NotNull GrCodeReferenceElement refElement) {
        super.visitCodeReferenceElement(refElement);
        HighlightInfo info = accessibilityChecker.checkCodeReferenceElement(refElement);
        if (info != null) {
          registerProblem(holder, info, refElement);
        }
      }

      private void registerProblem(ProblemsHolder holder, HighlightInfo info, PsiElement e) {
        if (info.getSeverity() == HighlightSeverity.ERROR) {
          holder.registerProblem(e, info.getDescription());
        }
      }
    });
  }
}
