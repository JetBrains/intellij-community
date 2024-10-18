// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.coverage;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ProjectViewNode;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.SmartPsiElementPointer;
import org.jetbrains.annotations.NotNull;


final class CoverageProjectViewDirectoryNodeDecorator extends AbstractCoverageProjectViewNodeDecorator {
  @Override
  public void decorate(@NotNull ProjectViewNode node, @NotNull PresentationData data) {
    Project project = node.getProject();
    if (project == null) {
      return;
    }

    final CoverageDataManager manager = CoverageDataManager.getInstance(project);
    if (manager == null) return;
    for (CoverageSuitesBundle suite : manager.activeSuites()) {
      decorateBundle(node, data, suite, project, manager);
    }

  }

  private static void decorateBundle(ProjectViewNode node,
                                     PresentationData data,
                                     CoverageSuitesBundle currentSuite,
                                     Project project,
                                     CoverageDataManager manager) {
    final CoverageAnnotator coverageAnnotator = currentSuite == null ? null : currentSuite.getAnnotator(project);
    if (coverageAnnotator == null) {
      // N/A
      return;
    }

    final Object value = node.getValue();
    PsiElement element = null;
    if (value instanceof PsiElement) {
      element = (PsiElement)value;
    }
    else if (value instanceof SmartPsiElementPointer) {
      element = ((SmartPsiElementPointer<?>)value).getElement();
    }

    String informationString = null;
    if (element instanceof PsiDirectory) {
      informationString = coverageAnnotator.getDirCoverageInformationString((PsiDirectory)element, currentSuite, manager);
    } else if (element instanceof PsiFile) {
      informationString = coverageAnnotator.getFileCoverageInformationString((PsiFile)element, currentSuite, manager);
    }

    if (informationString != null) {
      data.setLocationString(informationString);
    }
  }
}
