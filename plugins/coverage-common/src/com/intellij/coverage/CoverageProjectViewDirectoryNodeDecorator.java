// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.coverage;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ProjectViewNode;
import com.intellij.openapi.project.Project;
import com.intellij.packageDependencies.ui.PackageDependenciesNode;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.ui.ColoredTreeCellRenderer;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
final class CoverageProjectViewDirectoryNodeDecorator extends AbstractCoverageProjectViewNodeDecorator {
  CoverageProjectViewDirectoryNodeDecorator(@NotNull Project project) {
    super(project);
  }

  @Override
  public void decorate(PackageDependenciesNode node, ColoredTreeCellRenderer cellRenderer) {
    final PsiElement element = node.getPsiElement();
    if (element == null || !element.isValid()) {
      return;
    }

    Project project = element.getProject();

    final CoverageDataManager manager = getCoverageDataManager(project);
    if (manager == null) {
      return;
    }

    final CoverageSuitesBundle currentSuite = manager.getCurrentSuitesBundle();
    final CoverageAnnotator coverageAnnotator = currentSuite != null ? currentSuite.getAnnotator(project) : null;
    if (coverageAnnotator == null) {
      // N/A
      return;
    }

    String informationString = null;
    if (element instanceof PsiDirectory) {
      informationString = coverageAnnotator.getDirCoverageInformationString((PsiDirectory) element, currentSuite, manager);
    }
    else if (element instanceof PsiFile) {
      informationString = coverageAnnotator.getFileCoverageInformationString((PsiFile)element, currentSuite, manager);
    }
    if (informationString != null) {
      appendCoverageInfo(cellRenderer, informationString);
    }
  }

  @Override
  public void decorate(ProjectViewNode node, PresentationData data) {
    Project project = node.getProject();
    if (project == null) {
      return;
    }

    final CoverageDataManager manager = getCoverageDataManager(project);
    if (manager == null) return;
    final CoverageSuitesBundle currentSuite = manager.getCurrentSuitesBundle();

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
      element = ((SmartPsiElementPointer)value).getElement();
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
