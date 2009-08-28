package com.intellij.coverage;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ProjectViewNode;
import com.intellij.ide.projectView.ProjectViewNodeDecorator;
import com.intellij.ide.projectView.impl.nodes.PackageElement;
import com.intellij.packageDependencies.ui.PackageDependenciesNode;
import com.intellij.psi.*;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;

/**
 * @author yole
 */
public class CoverageProjectViewNodeDecorator implements ProjectViewNodeDecorator {
  private final CoverageDataManager myCoverageDataManager;

  public CoverageProjectViewNodeDecorator(CoverageDataManager coverageDataManager) {
    myCoverageDataManager = coverageDataManager;
  }

  public void decorate(PackageDependenciesNode node, ColoredTreeCellRenderer cellRenderer) {
    final PsiElement element = node.getPsiElement();
    if (element == null || !element.isValid()) {
      return;
    }
    if (element instanceof PsiClass) {
      final String qName = ((PsiClass)element).getQualifiedName();
      if (qName != null) {
        appendCoverageInfo(cellRenderer, myCoverageDataManager.getClassCoverageInformationString(qName));
      }
    }
    else if (element instanceof PsiDirectory) {
      final String informationString = myCoverageDataManager.getDirCoverageInformationString((PsiDirectory) element);
      if (informationString != null) {
        appendCoverageInfo(cellRenderer, informationString);
      }
    }
  }

  private static void appendCoverageInfo(ColoredTreeCellRenderer cellRenderer, String coverageInfo) {
    if (coverageInfo != null) {
      cellRenderer.append(" (" + coverageInfo + ")", SimpleTextAttributes.GRAY_ATTRIBUTES);
    }
  }

  public void decorate(ProjectViewNode node, PresentationData data) {
    final Object value = node.getValue();
    PsiElement element = null;
    if (value instanceof PsiElement) {
      element = (PsiElement)value;
    }
    else if (value instanceof SmartPsiElementPointer) {
      element = ((SmartPsiElementPointer)value).getElement();
    }
    else if (value instanceof PackageElement) {
      PackageElement packageElement = (PackageElement)value;
      final String coverageString = myCoverageDataManager.getPackageCoverageInformationString(packageElement.getPackage().getQualifiedName(),
                                                                                              packageElement.getModule());
      data.setLocationString(coverageString);
    }

    if (element instanceof PsiClass) {
      final String qName = ((PsiClass)element).getQualifiedName();
      if (qName != null) {
        data.setLocationString(myCoverageDataManager.getClassCoverageInformationString(qName));
      }
    }
    else if (element instanceof PsiDirectory) {
      final String informationString = myCoverageDataManager.getDirCoverageInformationString((PsiDirectory) element);
      if (informationString != null) {
        data.setLocationString(informationString);
      }
    }
  }

}
