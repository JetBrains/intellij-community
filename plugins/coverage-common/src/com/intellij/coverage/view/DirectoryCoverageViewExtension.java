package com.intellij.coverage.view;

import com.intellij.coverage.CoverageAnnotator;
import com.intellij.coverage.CoverageSuitesBundle;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.util.ui.ColumnInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * User: anna
 * Date: 1/9/12
 */
public class DirectoryCoverageViewExtension extends CoverageViewExtension {
  private final CoverageAnnotator myAnnotator;

  public DirectoryCoverageViewExtension(Project project,
                                        CoverageAnnotator annotator,
                                        CoverageSuitesBundle suitesBundle,
                                        CoverageViewManager.StateBean stateBean) {
    super(project, suitesBundle, stateBean);
    myAnnotator = annotator;
  }

  @Override
  public ColumnInfo[] createColumnInfos() {
    return new ColumnInfo[]{new ElementColumnInfo(), new PercentageCoverageColumnInfo(1, "Statistics, %", mySuitesBundle, myStateBean)};
  }

  @Override
  public String getSummaryForNode(AbstractTreeNode node) {
    return myAnnotator.getDirCoverageInformationString((PsiDirectory)node.getValue(), mySuitesBundle,
                                                       myCoverageDataManager) + " in '" + node.toString() + "'";
  }

  @Override
  public String getSummaryForRootNode(AbstractTreeNode childNode) {
    final Object value = childNode.getValue();
    return myAnnotator.getDirCoverageInformationString(((PsiDirectory)value), mySuitesBundle, myCoverageDataManager);
  }

  @Override
  public String getPercentage(int columnIdx, AbstractTreeNode node) {
    final Object value = node.getValue();
    if (value instanceof PsiFile) {
      return myAnnotator.getFileCoverageInformationString((PsiFile)value, mySuitesBundle, myCoverageDataManager);
    }
    return value != null ? myAnnotator.getDirCoverageInformationString((PsiDirectory)value, mySuitesBundle, myCoverageDataManager) : null;
  }


  @Override
  public PsiElement getParentElement(PsiElement element) {
    final PsiFile containingFile = element.getContainingFile();
    if (containingFile != null) {
      return containingFile.getContainingDirectory();
    }
    return null;
  }

  @Override
  public AbstractTreeNode createRootNode() {
    final VirtualFile baseDir = myProject.getBaseDir();
    return new CoverageListRootNode(myProject, PsiManager.getInstance(myProject).findDirectory(baseDir), mySuitesBundle, myStateBean);
  }

  @Override
  public List<AbstractTreeNode> getChildrenNodes(AbstractTreeNode node) {
    List<AbstractTreeNode> children = new ArrayList<>();
    if (node instanceof CoverageListNode) {
      final Object val = node.getValue();
      if (val instanceof PsiFile || val == null) return Collections.emptyList();
      final PsiDirectory psiDirectory = (PsiDirectory)val;
      final PsiDirectory[] subdirectories = ApplicationManager.getApplication().runReadAction(new Computable<PsiDirectory[]>() {
        @Override
        public PsiDirectory[] compute() {
          return psiDirectory.getSubdirectories(); 
        }
      });
      for (PsiDirectory subdirectory : subdirectories) {
        children.add(new CoverageListNode(myProject, subdirectory, mySuitesBundle, myStateBean));
      }
      final PsiFile[] psiFiles = ApplicationManager.getApplication().runReadAction(new Computable<PsiFile[]>() {
        @Override
        public PsiFile[] compute() {
          return psiDirectory.getFiles();
        }
      });
      for (PsiFile psiFile : psiFiles) {
        children.add(new CoverageListNode(myProject, psiFile, mySuitesBundle, myStateBean));
      }

      for (AbstractTreeNode childNode : children) {
        childNode.setParent(node);
      }
    }
    return children;
  }
}
