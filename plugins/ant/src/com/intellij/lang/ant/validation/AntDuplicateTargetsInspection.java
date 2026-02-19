// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.ant.validation;

import com.intellij.lang.ant.AntBundle;
import com.intellij.lang.ant.dom.AntDomProject;
import com.intellij.lang.ant.dom.AntDomTarget;
import com.intellij.lang.ant.dom.TargetResolver;
import com.intellij.openapi.util.Comparing;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.highlighting.DomElementAnnotationHolder;
import com.intellij.util.xml.highlighting.DomHighlightingHelper;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public final class AntDuplicateTargetsInspection extends AntInspection {

  private static final @NonNls String SHORT_NAME = "AntDuplicateTargetsInspection";

  @Override
  public @NonNls @NotNull String getShortName() {
    return SHORT_NAME;
  }

  @Override
  protected void checkDomElement(@NotNull DomElement element, final @NotNull DomElementAnnotationHolder holder, @NotNull DomHighlightingHelper helper) {
    if (element instanceof AntDomProject project) {
      TargetResolver.validateDuplicateTargets(project.getContextAntProject(), new TargetResolver.TargetSink() {
        @Override
        public void duplicateTargetDetected(AntDomTarget existingTarget, AntDomTarget duplicatingTarget, String targetEffectiveName) {
          final AntDomProject existingTargetProj = existingTarget.getAntProject();
          final AntDomProject duplucatingTargetProj = duplicatingTarget.getAntProject();
          final boolean isFromDifferentFiles = !Comparing.equal(existingTargetProj, duplucatingTargetProj);
          if (project.equals(existingTargetProj)) {
            final String duplicatedMessage = isFromDifferentFiles?
              AntBundle.message("target.is.duplicated.in.imported.file", targetEffectiveName, duplucatingTargetProj != null? duplucatingTargetProj.getName() : "") :
              AntBundle.message("target.is.duplicated", targetEffectiveName);
            holder.createProblem(existingTarget.getName(), duplicatedMessage);
          }
          if (project.equals(duplucatingTargetProj)) {
            final String duplicatedMessage = isFromDifferentFiles?
              AntBundle.message("target.is.duplicated.in.imported.file", targetEffectiveName, existingTargetProj != null? existingTargetProj.getName() : "") :
              AntBundle.message("target.is.duplicated", targetEffectiveName);
            holder.createProblem(duplicatingTarget.getName(), duplicatedMessage);
          }
        }
      });
    }
  }
}
