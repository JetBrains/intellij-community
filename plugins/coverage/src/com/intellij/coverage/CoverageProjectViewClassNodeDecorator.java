// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.coverage;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ProjectViewNode;
import com.intellij.ide.projectView.impl.nodes.PackageElement;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.packageDependencies.ui.PackageDependenciesNode;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.ui.ColoredTreeCellRenderer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class CoverageProjectViewClassNodeDecorator extends AbstractCoverageProjectViewNodeDecorator {
  CoverageProjectViewClassNodeDecorator(@NotNull Project project) {
    super(project);
  }

  @Override
  public void decorate(PackageDependenciesNode node, ColoredTreeCellRenderer cellRenderer) {
    final PsiElement element = node.getPsiElement();
    if (element == null || !element.isValid() || !(element instanceof PsiClass)) {
      return;
    }

    final Project project = element.getProject();

    final CoverageDataManager dataManager = getCoverageDataManager(project);
    final JavaCoverageAnnotator javaCovAnnotator = getCovAnnotator(dataManager, project);
    // This decorator is applicable only to JavaCoverageAnnotator
    if (javaCovAnnotator == null) {
      return;
    }

    final String qName = ((PsiClass)element).getQualifiedName();
    if (qName != null) {
      appendCoverageInfo(cellRenderer, javaCovAnnotator.getClassCoverageInformationString(qName, dataManager));
    }
  }

  @Override
  public void decorate(ProjectViewNode node, PresentationData data) {
    final Project project = node.getProject();
    if (project == null) {
      return;
    }

    final CoverageDataManager coverageDataManager = getCoverageDataManager(project);
    final JavaCoverageAnnotator javaCovAnnotator = getCovAnnotator(coverageDataManager, project);
    // This decorator is applicable only to JavaCoverageAnnotator
    if (javaCovAnnotator == null) {
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
    else if (value instanceof PackageElement) {
      PackageElement packageElement = (PackageElement)value;
      final String coverageString = javaCovAnnotator.getPackageCoverageInformationString(packageElement.getPackage(),
                                                                                         packageElement.getModule(),
                                                                                         coverageDataManager);
      data.setLocationString(coverageString);
    }

    if (element instanceof PsiClass) {
      final GlobalSearchScope searchScope = coverageDataManager.getCurrentSuitesBundle().getSearchScope(project);
      final VirtualFile vFile = PsiUtilCore.getVirtualFile(element);
      if (vFile != null && searchScope.contains(vFile)) {
        final String qName = ((PsiClass)element).getQualifiedName();
        if (qName != null) {
          data.setLocationString(javaCovAnnotator.getClassCoverageInformationString(qName, coverageDataManager));
        }
      }
    }
    else if (element instanceof PsiNamedElement &&
             // handled in CoverageProjectViewDirectoryNodeDecorator
             !(element instanceof PsiFile || element instanceof PsiDirectory)) {
      for (JavaCoverageEngineExtension extension : JavaCoverageEngineExtension.EP_NAME.getExtensions()) {
        final PackageAnnotator.ClassCoverageInfo info = extension.getSummaryCoverageInfo(javaCovAnnotator, (PsiNamedElement)element);
        if (info != null) {
          data.setLocationString(JavaCoverageAnnotator.getClassCoverageInformationString(info, coverageDataManager));
          break;
        }
      }
    }
  }

  @Nullable
  private static JavaCoverageAnnotator getCovAnnotator(@Nullable CoverageDataManager dataManager, @NotNull Project project) {
    if (dataManager == null) {
      return null;
    }

    CoverageSuitesBundle currentSuite = dataManager.getCurrentSuitesBundle();
    if (currentSuite != null) {
      final CoverageAnnotator coverageAnnotator = currentSuite.getAnnotator(project);
      if (coverageAnnotator instanceof JavaCoverageAnnotator) {
        return (JavaCoverageAnnotator) coverageAnnotator;
      }
    }
    return null;
  }
}