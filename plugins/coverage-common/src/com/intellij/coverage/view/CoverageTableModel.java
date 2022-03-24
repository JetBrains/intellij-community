package com.intellij.coverage.view;

import com.intellij.coverage.CoverageEngine;
import com.intellij.coverage.CoverageSuitesBundle;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.ui.tree.AsyncTreeModel;
import com.intellij.ui.tree.StructureTreeModel;
import com.intellij.ui.tree.TreeVisitor;
import com.intellij.ui.treeStructure.treetable.TreeTableModel;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.SortableColumnModel;
import com.intellij.util.ui.tree.AbstractTreeModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.Promise;

import javax.swing.*;
import javax.swing.event.TreeModelEvent;
import javax.swing.event.TreeModelListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import java.util.Comparator;
import java.util.function.Consumer;

class CoverageTableModel extends AbstractTreeModel implements TreeTableModel, SortableColumnModel, TreeModelListener, TreeVisitor.Acceptor {
  private final ColumnInfo[] COLUMN_INFOS;
  private final AsyncTreeModel myAsyncModel;
  private final StructureTreeModel<CoverageViewTreeStructure> myStructureModel;
  private JTree myTree;

  CoverageTableModel(@NotNull CoverageSuitesBundle suitesBundle,
                     CoverageViewManager.StateBean stateBean,
                     Project project,
                     CoverageViewTreeStructure structure) {
    final CoverageEngine coverageEngine = suitesBundle.getCoverageEngine();
    COLUMN_INFOS = coverageEngine.createCoverageViewExtension(project, suitesBundle, stateBean).createColumnInfos();
    myStructureModel = new StructureTreeModel<>(structure, this);
    myAsyncModel = new AsyncTreeModel(myStructureModel, true, this);
    myAsyncModel.addTreeModelListener(this);
  }

  public void reset() {
    myStructureModel.invalidate();
  }

  public void makeVisible(CoverageListNode node, Consumer<TreePath> onSuccess) {
    myStructureModel.makeVisible(node, myTree, onSuccess);
  }

  public void setComparator(Comparator<? super NodeDescriptor<?>> comparator) {
    myStructureModel.setComparator(comparator);
  }

  @Override
  public Object getRoot() {
    return myAsyncModel.getRoot();
  }

  @Override
  public Object getChild(Object parent, int index) {
    return myAsyncModel.getChild(parent, index);
  }

  @Override
  public int getChildCount(Object parent) {
    return myAsyncModel.getChildCount(parent);
  }

  @Override
  public boolean isLeaf(Object node) {
    return myAsyncModel.isLeaf(node);
  }

  @Override
  public int getIndexOfChild(Object parent, Object child) {
    return myAsyncModel.getIndexOfChild(parent, child);
  }

  @Override
  public @NotNull Promise<TreePath> accept(@NotNull TreeVisitor visitor) {
    return myAsyncModel.accept(visitor);
  }

  @Override
  public boolean isCellEditable(Object node, int column) {
    return false;
  }

  @Override
  public void setValueAt(Object aValue, Object node, int column) {
  }

  @Override
  public void setTree(JTree tree) {
    myTree = tree;
  }

  @Override
  public int getColumnCount() {
    return COLUMN_INFOS.length;
  }

  @Override
  public String getColumnName(int column) {
    return COLUMN_INFOS[column].getName();
  }

  @Override
  public Class getColumnClass(int column) {
    if (column == 0) return TreeTableModel.class;
    return COLUMN_INFOS[column].getColumnClass();
  }

  @Override
  public Object getValueAt(Object node, int column) {
    final CoverageListNode coverageNode = getCoverageNode(node);
    if (coverageNode != null) {
      return COLUMN_INFOS[column].valueOf(coverageNode);
    }
    return "";
  }

  public CoverageListNode getCoverageNode(Object node) {
    if (node instanceof CoverageListNode) {
      return (CoverageListNode)node;
    }
    if (node instanceof DefaultMutableTreeNode) {
      final Object userObject = ((DefaultMutableTreeNode)node).getUserObject();
      if (userObject instanceof CoverageListNode) {
        return (CoverageListNode)userObject;
      }
    }
    return null;
  }

  @Override
  public ColumnInfo[] getColumnInfos() {
    return COLUMN_INFOS;
  }

  @Override
  public boolean isSortable() {
    return true;
  }

  @Override
  public void setSortable(boolean aBoolean) {
  }

  @Override
  public Object getRowValue(int row) {
    if (myTree == null) return null;
    final TreePath path = myTree.getPathForRow(row);
    if (path == null) return null;
    return path.getLastPathComponent();
  }

  @Override
  public @Nullable RowSorter.SortKey getDefaultSortKey() {
    return null;
  }

  @Override
  public void treeNodesChanged(TreeModelEvent e) {
    treeNodesChanged(e.getTreePath(), e.getChildIndices(), e.getChildren());
  }

  @Override
  public void treeNodesInserted(TreeModelEvent e) {
    treeNodesInserted(e.getTreePath(), e.getChildIndices(), e.getChildren());
  }

  @Override
  public void treeNodesRemoved(TreeModelEvent e) {
    treeNodesRemoved(e.getTreePath(), e.getChildIndices(), e.getChildren());
  }

  @Override
  public void treeStructureChanged(TreeModelEvent e) {
    treeStructureChanged(e.getTreePath(), e.getChildIndices(), e.getChildren());
  }
}
