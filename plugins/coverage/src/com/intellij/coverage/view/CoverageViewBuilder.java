package com.intellij.coverage.view;

import com.intellij.coverage.CoverageAnnotator;
import com.intellij.coverage.CoverageDataManager;
import com.intellij.coverage.JavaCoverageAnnotator;
import com.intellij.ide.commander.AbstractListBuilder;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.ide.util.treeView.AbstractTreeStructure;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiPackage;
import com.intellij.ui.TableUtil;
import com.intellij.ui.table.JBTable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * User: anna
 * Date: 1/2/12
 */
public class CoverageViewBuilder extends AbstractListBuilder {
  private final JBTable myTable;

  CoverageViewBuilder(final Project project,
                      final JList list,
                      final Model model,
                      final AbstractTreeStructure treeStructure, final Comparator comparator, JBTable table) {
    super(project, list, model, treeStructure, comparator, false);
    myTable = table;
    buildRoot();
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
  protected List<AbstractTreeNode> getAllAcceptableNodes(Object[] childElements, VirtualFile file) {
    ArrayList<AbstractTreeNode> result = new ArrayList<AbstractTreeNode>();

    for (Object childElement1 : childElements) {
      CoverageListNode childElement = (CoverageListNode)childElement1;
      if (childElement.contains(file)) result.add(childElement);
    }

    return result;
  }

  @Override
  protected void updateParentTitle() {
    if (myParentTitle == null) return;

    AbstractTreeNode node = getParentNode();
    if (node instanceof CoverageListNode && node != myTreeStructure.getRootElement()) {
      final Object value = node.getValue();
      final CoverageAnnotator annotator = ((CoverageListNode)node).getBundle().getAnnotator(myProject);
      myParentTitle.setText("Coverage Summary for Package \'" + node.toString() + "\': " + ((JavaCoverageAnnotator)annotator).getPackageCoverageInformationString(
        (PsiPackage)value, null, CoverageDataManager.getInstance(myProject)));
    }
    else {
      final AbstractTreeNode selectedNode = (AbstractTreeNode)getSelectedValue();
      if (selectedNode != null) {
        final Object value = selectedNode.getValue();
        final CoverageAnnotator annotator = ((CoverageListNode)selectedNode).getBundle().getAnnotator(myProject);
        myParentTitle.setText("Coverage Summary for \'all classes in scope\': " + ((JavaCoverageAnnotator)annotator).getPackageCoverageInformationString(
          (PsiPackage)value, null, CoverageDataManager.getInstance(myProject)));
      }
    }
  }

  @Override
  protected Object getSelectedValue() {
    final int row = myTable.getSelectedRow();
    if (row == -1) return null;
    return myModel.getElementAt(row); //todo map indices
  }

  @Override
  protected void ensureSelectionExist() {
    TableUtil.ensureSelectionExists(myTable);
  }

  @Override
  protected void selectItem(int i) {
    TableUtil.selectRows(myTable, new int[]{i});
  }
}
