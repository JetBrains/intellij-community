package com.intellij.coverage;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ProjectViewNode;
import com.intellij.packageDependencies.ui.PackageDependenciesNode;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.ui.ColoredTreeCellRenderer;

/**
 * @author yole
 */
public class CoverageProjectViewDirectoryNodeDecorator extends AbstractCoverageProjectViewNodeDecorator {
  public CoverageProjectViewDirectoryNodeDecorator(final CoverageDataManager coverageDataManager) {
    super(coverageDataManager);
  }

  public void decorate(PackageDependenciesNode node, ColoredTreeCellRenderer cellRenderer) {
    final PsiElement element = node.getPsiElement();
    if (element == null || !element.isValid()) {
      return;
    }

    final CoverageDataManager manager = getCoverageDataManager();
    final CoverageSuitesBundle currentSuite = manager.getCurrentSuitesBundle();
    final CoverageAnnotator coverageAnnotator = currentSuite != null ? currentSuite.getAnnotator(element.getProject()) : null;
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

  public void decorate(ProjectViewNode node, PresentationData data) {
    final CoverageDataManager manager = getCoverageDataManager();
    final CoverageSuitesBundle currentSuite = manager.getCurrentSuitesBundle();
    final CoverageAnnotator coverageAnnotator = currentSuite != null ? currentSuite.getAnnotator(node.getProject())
                                                                     : null;
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
