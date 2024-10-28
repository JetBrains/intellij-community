// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.coverage;

import com.intellij.coverage.analysis.JavaCoverageAnnotator;
import com.intellij.coverage.analysis.PackageAnnotator;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ProjectViewNode;
import com.intellij.ide.projectView.impl.nodes.PackageElement;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiUtilCore;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class CoverageProjectViewClassNodeDecorator extends AbstractCoverageProjectViewNodeDecorator {
  @Override
  public void decorate(@NotNull ProjectViewNode node, @NotNull PresentationData data) {
    final Project project = node.getProject();
    if (project == null) {
      return;
    }
    if (!isShowingCoverageInProjectViewEnabled(project)) return;

    final CoverageDataManager coverageDataManager = CoverageDataManager.getInstance(project);
    CoverageSuitesBundle javaSuite = getJavaSuite(coverageDataManager, project);
    // This decorator is applicable only to java suites
    if (javaSuite == null) return;
    final JavaCoverageAnnotator javaCovAnnotator = (JavaCoverageAnnotator) javaSuite.getAnnotator(project);

    final Object value = node.getValue();
    PsiElement element = null;
    if (value instanceof PsiElement psiElement) {
      element = psiElement;
    }
    else if (value instanceof SmartPsiElementPointer<?> smartPointer) {
      element = smartPointer.getElement();
    }
    else if (value instanceof PackageElement packageElement) {
      final String coverageString = javaCovAnnotator.getPackageCoverageInformationString(packageElement.getPackage(),
                                                                                         packageElement.getModule(),
                                                                                         coverageDataManager);
      data.setLocationString(coverageString);
    }

    if (element instanceof PsiClass) {
      final GlobalSearchScope searchScope = javaSuite.getSearchScope(project);
      final VirtualFile vFile = PsiUtilCore.getVirtualFile(element);
      if (vFile != null && searchScope.contains(vFile)) {
        final String qName = ((PsiClass)element).getQualifiedName();
        if (qName != null) {
          data.setLocationString(javaCovAnnotator.getClassCoverageInformationString(qName, coverageDataManager));
        }
      }
    }
    else if (element instanceof PsiNamedElement namedElement &&
             // handled in CoverageProjectViewDirectoryNodeDecorator
             !(element instanceof PsiFile || element instanceof PsiDirectory)) {
      for (JavaCoverageEngineExtension extension : JavaCoverageEngineExtension.EP_NAME.getExtensions()) {
        final PackageAnnotator.ClassCoverageInfo info = extension.getSummaryCoverageInfo(javaCovAnnotator, namedElement);
        if (info != null) {
          data.setLocationString(JavaCoverageAnnotator.getClassCoverageInformationString(info, coverageDataManager));
          break;
        }
      }
    }
  }

  private static CoverageSuitesBundle getJavaSuite(@Nullable CoverageDataManager dataManager, @NotNull Project project) {
    if (dataManager == null) {
      return null;
    }
    for (CoverageSuitesBundle bundle : dataManager.activeSuites()) {
      final CoverageAnnotator coverageAnnotator = bundle.getAnnotator(project);
      if (coverageAnnotator instanceof JavaCoverageAnnotator) {
        return bundle;
      }
    }
    return null;
  }
}