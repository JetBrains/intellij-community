/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.lang.ant.validation;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.lang.ant.AntBundle;
import com.intellij.lang.ant.AntSupport;
import com.intellij.lang.ant.dom.AntDomProject;
import com.intellij.lang.ant.dom.AntDomTarget;
import com.intellij.lang.ant.dom.TargetResolver;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlElement;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class AntDuplicateTargetsInspection extends AntInspection {

  @NonNls private static final String SHORT_NAME = "AntDuplicateTargetsInspection";

  @Nls
  @NotNull
  public String getDisplayName() {
    return AntBundle.message("ant.duplicate.targets.inspection");
  }

  @NonNls
  @NotNull
  public String getShortName() {
    return SHORT_NAME;
  }

  @Nullable
  public ProblemDescriptor[] checkFile(@NotNull final PsiFile file, @NotNull final InspectionManager manager, final boolean isOnTheFly) {
    final AntDomProject project = AntSupport.getAntDomProject(file);
    if (project == null) {
      return null;
    }
    final List<ProblemDescriptor> problems = new ArrayList<ProblemDescriptor>();
    TargetResolver.validateDuplicateTargets(project.getContextAntProject(), new TargetResolver.TargetSink() {
      public void duplicateTargetDetected(AntDomTarget existingTarget, AntDomTarget duplicatingTarget, String targetEffectiveName) {
        final PsiFile existingTargetFile = getContainingFile(existingTarget);
        final PsiFile duplucatingTargetFile = getContainingFile(duplicatingTarget);
        final boolean isFromDifferentFiles = !Comparing.equal(existingTargetFile, duplucatingTargetFile);
        if (file.equals(existingTargetFile)) {
          final PsiElement psi = existingTarget.getXmlElement();
          if (psi != null) {
            final String duplicatedMessage = isFromDifferentFiles? 
              AntBundle.message("target.is.duplicated.in.imported.file", targetEffectiveName, duplucatingTargetFile != null? duplucatingTargetFile.getName() : "") : 
              AntBundle.message("target.is.duplicated", targetEffectiveName);
            problems.add(manager.createProblemDescriptor(
              psi, duplicatedMessage, isOnTheFly, LocalQuickFix.EMPTY_ARRAY, ProblemHighlightType.GENERIC_ERROR_OR_WARNING)
            );
          }
        }
        if (file.equals(duplucatingTargetFile)) {
          final PsiElement psi = duplicatingTarget.getXmlElement();
          if (psi != null) {
            final String duplicatedMessage = isFromDifferentFiles? 
              AntBundle.message("target.is.duplicated.in.imported.file", targetEffectiveName, existingTargetFile != null? existingTargetFile.getName() : "") : 
              AntBundle.message("target.is.duplicated", targetEffectiveName);
            problems.add(manager.createProblemDescriptor(
              psi, duplicatedMessage, isOnTheFly, LocalQuickFix.EMPTY_ARRAY, ProblemHighlightType.GENERIC_ERROR_OR_WARNING)
            );
          }
        }
      }
    });
    final int problemCount = problems.size();
    return problemCount > 0? problems.toArray(new ProblemDescriptor[problemCount]) : null;
  }

  @Nullable 
  private static PsiFile getContainingFile(AntDomTarget target) {
    final XmlElement xmlElement = target.getXmlElement();
    return xmlElement != null? xmlElement.getContainingFile() : null;
  }
  
}
