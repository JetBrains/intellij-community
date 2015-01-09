package com.intellij.coverage;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ProjectViewNode;
import com.intellij.ide.projectView.impl.nodes.PackageElement;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.packageDependencies.ui.PackageDependenciesNode;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.ui.ColoredTreeCellRenderer;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public class CoverageProjectViewClassNodeDecorator extends AbstractCoverageProjectViewNodeDecorator {
  public CoverageProjectViewClassNodeDecorator(final CoverageDataManager coverageDataManager) {
    super(coverageDataManager);
  }


  public void decorate(PackageDependenciesNode node, ColoredTreeCellRenderer cellRenderer) {
    final PsiElement element = node.getPsiElement();
    if (element == null || !element.isValid()) {
      return;
    }

    final CoverageDataManager dataManager = getCoverageDataManager();
    final CoverageSuitesBundle currentSuite = dataManager.getCurrentSuitesBundle();
    final Project project = element.getProject();

    final JavaCoverageAnnotator javaCovAnnotator = getCovAnnotator(currentSuite, project);
    // This decorator is applicable only to JavaCoverageAnnotator
    if (javaCovAnnotator == null) {
      return;
    }

    if (element instanceof PsiClass) {
      final String qName = ((PsiClass)element).getQualifiedName();
      if (qName != null) {
        appendCoverageInfo(cellRenderer, javaCovAnnotator.getClassCoverageInformationString(qName, dataManager));
      }
    }
  }

  public void decorate(ProjectViewNode node, PresentationData data) {
    final CoverageDataManager coverageDataManager = getCoverageDataManager();
    final CoverageSuitesBundle currentSuite = coverageDataManager.getCurrentSuitesBundle();

    final Project project = node.getProject();
    final JavaCoverageAnnotator javaCovAnnotator = getCovAnnotator(currentSuite, project);
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
      element = ((SmartPsiElementPointer)value).getElement();
    }
    else if (value instanceof PackageElement) {
      PackageElement packageElement = (PackageElement)value;
      final String coverageString = javaCovAnnotator.getPackageCoverageInformationString(packageElement.getPackage(),
                                                                                         packageElement.getModule(),
                                                                                         coverageDataManager);
      data.setLocationString(coverageString);
    }

    if (element instanceof PsiClass) {
      final GlobalSearchScope searchScope = currentSuite.getSearchScope(project);
      final VirtualFile vFile = PsiUtilCore.getVirtualFile(element);
      if (vFile != null && searchScope.contains(vFile)) {
        final String qName = ((PsiClass)element).getQualifiedName();
        if (qName != null) {
          data.setLocationString(javaCovAnnotator.getClassCoverageInformationString(qName, coverageDataManager));
        }
      }
    }
  }

  @Nullable
  private static JavaCoverageAnnotator getCovAnnotator(final CoverageSuitesBundle currentSuite, Project project) {
    if (currentSuite != null) {
      final CoverageAnnotator coverageAnnotator = currentSuite.getAnnotator(project);
      if (coverageAnnotator instanceof JavaCoverageAnnotator) {
        return (JavaCoverageAnnotator) coverageAnnotator;
      }
    }
    return null;
  }
}