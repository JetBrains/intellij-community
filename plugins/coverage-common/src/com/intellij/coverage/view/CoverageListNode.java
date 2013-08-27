package com.intellij.coverage.view;

import com.intellij.codeInsight.navigation.NavigationUtil;
import com.intellij.coverage.CoverageSuitesBundle;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtilCore;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collection;

/**
* User: anna
* Date: 1/2/12
*/
public class CoverageListNode extends AbstractTreeNode {
  protected CoverageSuitesBundle myBundle;
  protected CoverageViewManager.StateBean myStateBean;
  private final FileStatusManager myFileStatusManager;

  public CoverageListNode(Project project, 
                          final PsiNamedElement classOrPackage,
                          CoverageSuitesBundle bundle,
                          CoverageViewManager.StateBean stateBean) {
    super(project, classOrPackage);
    myName = ApplicationManager.getApplication().runReadAction(new Computable<String>() {
      @Override
      public String compute() {
        return classOrPackage.getName(); 
      }
    });
    myBundle = bundle;
    myStateBean = stateBean;
    myFileStatusManager = FileStatusManager.getInstance(myProject);
  }

  @NotNull
  @Override
  public Collection<? extends AbstractTreeNode> getChildren() {
    final Object[] children = CoverageViewTreeStructure.getChildren(this, myBundle, myStateBean);
    return (Collection<CoverageListNode>)Arrays.asList((CoverageListNode[])children);
  }

  @Override
  protected void update(final PresentationData presentation) {
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        final Object value = getValue();
        if (value instanceof PsiNamedElement) {

          if (value instanceof PsiQualifiedNamedElement &&
              (myStateBean.myFlattenPackages && ((PsiNamedElement)value).getContainingFile() == null ||
               getParent() instanceof CoverageListRootNode)) {
            presentation.setPresentableText(((PsiQualifiedNamedElement)value).getQualifiedName());
          }
          else {
            presentation.setPresentableText(((PsiNamedElement)value).getName());
          }
          presentation.setIcon(((PsiElement)value).getIcon(0));
        }
      }
    });
  }

  @Override
  public FileStatus getFileStatus() {
    final PsiFile containingFile = ApplicationManager.getApplication().runReadAction(new Computable<PsiFile>() {
      @Nullable
      @Override
      public PsiFile compute() {
        Object value = getValue();
        if (value instanceof PsiElement && ((PsiElement)value).isValid()) {
          return ((PsiElement)value).getContainingFile();
        }
        return null;
      }
    });
    return containingFile != null ? myFileStatusManager.getStatus(containingFile.getVirtualFile()) : super.getFileStatus();
  }

  @Override
  public boolean canNavigate() {
    final Object value = getValue();
    return value instanceof PsiElement && ((PsiElement)value).isValid() && ((PsiElement)value).getContainingFile() != null;
  }

  @Override
  public boolean canNavigateToSource() {
    return canNavigate();
  }

  @Override
  public void navigate(boolean requestFocus) {
    if (canNavigate()) {
      final PsiNamedElement value = (PsiNamedElement)getValue();
      if (requestFocus) {
        NavigationUtil.activateFileWithPsiElement(value, true);
      }
      else if (value instanceof NavigationItem) {
        ((NavigationItem)value).navigate(requestFocus);
      }
    }
  }

  @Override
  public int getWeight() {
    return ApplicationManager.getApplication().runReadAction(new Computable<Integer>() {
      @Override
      public Integer compute() {
        //todo weighted
        final Object value = getValue();
        if (value instanceof PsiElement && ((PsiElement)value).getContainingFile() != null) return 40;
        return 30;
      }
    });
  }

  public boolean contains(VirtualFile file) {
    final Object value = getValue();
    if (value instanceof PsiElement) {
      final boolean equalContainingFile = Comparing.equal(PsiUtilCore.getVirtualFile((PsiElement)value), file);
      if (equalContainingFile) return true;
    }
    if (value instanceof PsiDirectory) {
      return contains(file, (PsiDirectory)value);
    }
    else if (value instanceof PsiDirectoryContainer) {
      final PsiDirectory[] directories = ((PsiDirectoryContainer)value).getDirectories();
      for (PsiDirectory directory : directories) {
        if (contains(file, directory)) return true;
      }
    }
    return false;
  }

  private boolean contains(VirtualFile file, PsiDirectory value) {
    if (myStateBean.myFlattenPackages) {
      return Comparing.equal(((PsiDirectory)value).getVirtualFile(), file.getParent());
    }

    if (VfsUtilCore.isAncestor(((PsiDirectory)value).getVirtualFile(), file, false)) {
      return true;
    }

    return false;
  }
}
