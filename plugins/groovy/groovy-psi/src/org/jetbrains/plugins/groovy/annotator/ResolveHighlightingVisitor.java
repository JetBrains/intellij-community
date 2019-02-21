// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.annotator;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.codeInspection.untypedUnresolvedAccess.GrUnresolvedAccessChecker;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase;
import org.jetbrains.plugins.groovy.lang.psi.GroovyRecursiveElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;

import java.util.List;

public class ResolveHighlightingVisitor extends GroovyRecursiveElementVisitor {
  private final GrUnresolvedAccessChecker myReferenceChecker;
  private final VisitorCallback myCallback;
  private int myTriggerCounter = 0;

  public ResolveHighlightingVisitor(@NotNull GroovyFileBase file, @NotNull Project project, @NotNull VisitorCallback callback) {
    myCallback = callback;
    myReferenceChecker = new GrUnresolvedAccessChecker(file, project);
  }

  @Override
  public void visitReferenceExpression(@NotNull GrReferenceExpression referenceExpression) {
    final int oldValue = myTriggerCounter;
    super.visitReferenceExpression(referenceExpression);
    if (oldValue != myTriggerCounter) return;

    List<HighlightInfo> infos = myReferenceChecker.checkReferenceExpression(referenceExpression);
    if (infos == null) return;
    infos.forEach(info -> {
      myCallback.trigger(referenceExpression, info);
      myTriggerCounter++;
    });
  }
}
