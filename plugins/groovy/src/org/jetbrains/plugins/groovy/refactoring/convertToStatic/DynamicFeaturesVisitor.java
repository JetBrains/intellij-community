// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.refactoring.convertToStatic;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.annotator.InaccessibleElementVisitor;
import org.jetbrains.plugins.groovy.annotator.ResolveHighlightingVisitor;
import org.jetbrains.plugins.groovy.annotator.VisitorCallback;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyRecursiveElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;

public class DynamicFeaturesVisitor extends GroovyRecursiveElementVisitor {
  private final VisitorCallback myCallback;
  private final InaccessibleElementVisitor accessibilityChecker;
  private final ResolveHighlightingVisitor resolveVisitor;

  public DynamicFeaturesVisitor(@NotNull GroovyFile file, @NotNull Project project, @NotNull VisitorCallback callback) {
    accessibilityChecker = new InaccessibleElementVisitor(file, project, callback);
    resolveVisitor = new ResolveHighlightingVisitor(file, project, callback);
    myCallback = callback;
  }

  @Override
  public void visitReferenceExpression(@NotNull GrReferenceExpression referenceExpression) {
    accessibilityChecker.visitReferenceExpression(referenceExpression);
    resolveVisitor.visitReferenceExpression(referenceExpression);
    if (referenceExpression.getType() == null) {
      PsiElement resolved = referenceExpression.resolve();
      if (!(resolved instanceof GrMethod) || !((GrMethod)resolved).isConstructor()) myCallback.trigger(referenceExpression, null);
    }
    super.visitReferenceExpression(referenceExpression);
  }

  @Override
  public void visitCodeReferenceElement(@NotNull GrCodeReferenceElement refElement) {
    accessibilityChecker.visitCodeReferenceElement(refElement);
    super.visitCodeReferenceElement(refElement);
  }
}
