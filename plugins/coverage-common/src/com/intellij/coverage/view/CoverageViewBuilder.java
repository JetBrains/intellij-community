// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.coverage.view;

import com.intellij.coverage.CoverageBundle;
import com.intellij.ide.commander.AbstractListBuilder;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.ide.util.treeView.AbstractTreeStructure;
import com.intellij.ide.util.treeView.AlphaComparator;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vcs.FileStatusListener;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.ui.TableUtil;
import com.intellij.ui.table.JBTable;
import com.intellij.util.concurrency.AppExecutorUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

public class CoverageViewBuilder extends AbstractListBuilder {
  private final JBTable myTable;
  private final FileStatusListener myFileStatusListener;
  private final CoverageViewExtension myCoverageViewExtension;

  CoverageViewBuilder(final Project project,
                      final JList list,
                      final Model model,
                      final AbstractTreeStructure treeStructure, final JBTable table) {
    super(project, list, model, treeStructure, AlphaComparator.INSTANCE, true);
    myTable = table;
    ProgressManager.getInstance().run(new Task.Backgroundable(project, CoverageBundle.message("coverage.report.building")) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        buildRoot();
      }

      @Override
      public void onSuccess() {
        ensureSelectionExist();
        updateParentTitle();
      }
    });
    myFileStatusListener = new FileStatusListener() {
      @Override
      public void fileStatusesChanged() {
        table.repaint();
      }

      @Override
      public void fileStatusChanged(@NotNull VirtualFile virtualFile) {
        table.repaint();
      }
    };
    myCoverageViewExtension = ((CoverageViewTreeStructure)myTreeStructure).myData
      .getCoverageEngine().createCoverageViewExtension(myProject, ((CoverageViewTreeStructure)myTreeStructure).myData,
                                                       ((CoverageViewTreeStructure)myTreeStructure).myStateBean);
    FileStatusManager.getInstance(myProject).addFileStatusListener(myFileStatusListener, this);
  }

  @Override
  protected boolean shouldEnterSingleTopLevelElement(Object rootChild) {
    return false;
  }

  @Override
  protected boolean shouldAddTopElement() {
    return false;
  }

  @Override
  protected boolean nodeIsAcceptableForElement(AbstractTreeNode node, Object element) {
    return Comparing.equal(node.getValue(), element);
  }

  @Override
  protected List<AbstractTreeNode<?>> getAllAcceptableNodes(Object[] childElements, VirtualFile file) {
    ArrayList<AbstractTreeNode<?>> result = new ArrayList<>();

    for (Object childElement1 : childElements) {
      CoverageListNode childElement = (CoverageListNode)childElement1;
      if (childElement.contains(file)) result.add(childElement);
    }

    return result;
  }

  @Override
  protected void updateParentTitle() {
    if (myParentTitle == null) return;

    final AbstractTreeNode parentNode = getParentNode();
    final AbstractTreeNode node = parentNode == null ? (AbstractTreeNode)myTreeStructure.getRootElement() : parentNode;
    ReadAction.nonBlocking(() -> node instanceof CoverageListRootNode
                                 ? myCoverageViewExtension.getSummaryForRootNode(node)
                                 : myCoverageViewExtension.getSummaryForNode(node))
      .finishOnUiThread(ModalityState.NON_MODAL, (@Nls String text) -> myParentTitle.setText(text))
      .submit(AppExecutorUtil.getAppExecutorService());
  }

  @Override
  public  Object getSelectedValue() {
    final int row = myTable.getSelectedRow();
    if (row == -1) return null;
    return myModel.getElementAt(myTable.convertRowIndexToModel(row));
  }

  @Override
  protected void ensureSelectionExist() {
    TableUtil.ensureSelectionExists(myTable);
  }

  @Override
  protected void selectItem(int i) {
    TableUtil.selectRows(myTable, new int[]{myTable.convertRowIndexToView(i)});
    TableUtil.scrollSelectionToVisible(myTable);
  }

  public boolean canSelect(VirtualFile file) {
    return myCoverageViewExtension.canSelectInCoverageView(file);
  }

  public void select(Object object) {
    ReadAction.nonBlocking(() -> {
      final PsiElement element = myCoverageViewExtension.getElementToSelect(object);
      final VirtualFile file = myCoverageViewExtension.getVirtualFile(object);
      selectElement(element, file);
    }).submit(AppExecutorUtil.getAppExecutorService());
  }
}
