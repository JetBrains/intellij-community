/*
 * Copyright 2000-2011 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.cvsSupport2.cvsoperations.cvsAdd.ui;

import com.intellij.CvsBundle;
import com.intellij.cvsSupport2.CvsActionPlaces;
import com.intellij.cvsSupport2.cvsoperations.cvsAdd.AddedFileInfo;
import com.intellij.cvsSupport2.keywordSubstitution.KeywordSubstitutionWrapper;
import com.intellij.cvsSupport2.ui.Options;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import com.intellij.ui.ComboBoxTableCellRenderer;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.dualView.TreeTableView;
import com.intellij.ui.treeStructure.treetable.ListTreeTableModelOnColumns;
import com.intellij.ui.treeStructure.treetable.TreeTable;
import com.intellij.ui.treeStructure.treetable.TreeTableModel;
import com.intellij.ui.treeStructure.treetable.TreeTableTree;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.table.ComboBoxTableCellEditor;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NotNull;
import org.netbeans.lib.cvsclient.command.KeywordSubstitution;

import javax.swing.*;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import javax.swing.tree.DefaultMutableTreeNode;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Observable;
import java.util.Observer;
import java.util.function.Supplier;

/**
 * author: lesya
 */
public class AddMultipleFilesOptionsDialog extends AbstractAddOptionsDialog {
  private final Collection<? extends AddedFileInfo> myRoots;
  private final static JCheckBox CHECKBOX = new JCheckBox();

  private final ColumnInfo INCLUDED = new ColumnInfo("") {

    @Override
    public Object valueOf(Object object) {
      return Boolean.valueOf(((AddedFileInfo)object).included());
    }

    @Override
    public Class getColumnClass() {
      return Boolean.class;
    }

    @Override
    public boolean isCellEditable(Object o) {
      return true;
    }

    @Override
    public void setValue(Object o, Object aValue) {
      final AddedFileInfo node = (AddedFileInfo)o;
      node.setIncluded(((Boolean)aValue).booleanValue());
      myModel.nodeChanged(node);
    }

    @Override
    public int getWidth(JTable table) {
      return CHECKBOX.getPreferredSize().width + 4;
    }
  };


  private static final ColumnInfo<AddedFileInfo, AddedFileInfo> FILE =
    new ColumnInfo<AddedFileInfo, AddedFileInfo>(CvsBundle.message("add.multiple.files.file.column.name")) {
      @Override
      public AddedFileInfo valueOf(AddedFileInfo object) {
        return object;
      }

      @Override
      public Class getColumnClass() {
        return TreeTableModel.class;
      }

      @Override
      public boolean isCellEditable(AddedFileInfo o) {
        return true;
      }
    };

  private static final ColumnInfo KEYWORD_SUBSTITUTION = new ColumnInfo(
    CvsBundle.message("add.multiple.files.keyword.substitution.column.name")) {
    @Override
    public Object valueOf(Object object) {
      return ((AddedFileInfo)object).getKeywordSubstitutionsWithSelection();
    }

    @Override
    public boolean isCellEditable(Object o) {
      AddedFileInfo addedFileInfo = (AddedFileInfo)o;
      if (addedFileInfo.getFile().isDirectory()) return false;
      return addedFileInfo.included();
    }

    @Override
    public void setValue(Object o, Object aValue) {
      final AddedFileInfo fileInfo = (AddedFileInfo)o;
      final KeywordSubstitutionWrapper substitutionWrapper = (KeywordSubstitutionWrapper)aValue;
      final KeywordSubstitution substitution = substitutionWrapper.getSubstitution();
      fileInfo.setKeywordSubstitution(substitution);
    }

    @Override
    public TableCellRenderer getRenderer(Object o) {
      AddedFileInfo addedFileInfo = (AddedFileInfo)o;
      if (addedFileInfo.getFile().isDirectory()) return TABLE_CELL_RENDERER;
      return ComboBoxTableCellRenderer.INSTANCE;
    }

    @Override
    public TableCellEditor getEditor(Object item) {
      return ComboBoxTableCellEditor.INSTANCE;
    }

    @Override
    public int getWidth(JTable table) {
      return table.getFontMetrics(table.getFont()).stringWidth(getName()) + 10;
    }
  };

  private final ColumnInfo[] COLUMNS = new ColumnInfo[]{INCLUDED, FILE, KEYWORD_SUBSTITUTION};

  private TreeTable myTreeTable;
  private ListTreeTableModelOnColumns myModel;
  private static final JPanel J_PANEL = new JPanel();
  private static final TableCellRenderer TABLE_CELL_RENDERER = new TableCellRenderer() {
    @Override
    public Component getTableCellRendererComponent(JTable table,
                                                   Object value,
                                                   boolean isSelected,
                                                   boolean hasFocus,
                                                   int row,
                                                   int column) {
      J_PANEL.setBackground(isSelected ? table.getSelectionBackground() : table.getBackground());
      return J_PANEL;
    }
  };
  private final Observer myObserver;


  public AddMultipleFilesOptionsDialog(Project project, Collection<? extends AddedFileInfo> roots, Options options) {
    super(project, options);

    myRoots = roots;
    myObserver = new Observer() {
          @Override
          public void update(Observable o, Object arg) {
            setOKButtonEnabling();
          }
        };

    for (AddedFileInfo myRoot : myRoots) {
      myRoot.addIncludedObserver(myObserver);
    }

    setTitle(CvsBundle.message("dialog.title.add.files.to.cvs"));
    createTree();
    expandAll();

    init();
    setOKButtonEnabling();
  }

  private void setOKButtonEnabling() {
    setOKActionEnabled(hasIncludedNodes());
  }

  private boolean hasIncludedNodes() {
    for (AddedFileInfo addedFileInfo : myRoots) {
      if (addedFileInfo.hasIncludedNodes()) return true;
    }
    return false;
  }

  @Override
  public void dispose() {
    super.dispose();
    for (AddedFileInfo myRoot : myRoots) {
      myRoot.removeIncludedObserver(myObserver);
    }
  }

  private void expandAll() {
    int row = 0;
    JTree tree = myTreeTable.getTree();
    while (row < tree.getRowCount()) {
      tree.expandRow(row);
      row++;
    }
  }

  private void createTree() {
    DefaultMutableTreeNode root = new DefaultMutableTreeNode();

    for (AddedFileInfo myRoot : myRoots) {
      root.add(myRoot);
    }

    myModel = new ListTreeTableModelOnColumns(root, COLUMNS);
    myTreeTable = new TreeTableView(myModel);

    int comboHeight = new JComboBox().getPreferredSize().height;
    int checkBoxHeight = new JCheckBox().getPreferredSize().height;
    myTreeTable.setMinRowHeight(Math.max(comboHeight, checkBoxHeight) + 2);
    myTreeTable.setRootVisible(false);
    final JTableHeader tableHeader = myTreeTable.getTableHeader();
    tableHeader.setReorderingAllowed(false);
    tableHeader.setResizingAllowed(false);
    final TreeTableTree tree = myTreeTable.getTree();
    myTreeTable.addKeyListener(new KeyAdapter() {
      @Override
      public void keyPressed(KeyEvent e) {
        final int keyCode = e.getKeyCode();
        if (keyCode == KeyEvent.VK_SPACE) {
          final int selectedColumn = myTreeTable.getSelectedColumn();
          if (selectedColumn == 0) {
            return;
          }
          final int[] selectedRows = myTreeTable.getSelectedRows();
          if (selectedRows.length == 0) {
            return;
          }
          final boolean included = !((AddedFileInfo)myTreeTable.getValueAt(selectedRows[0], 1)).included();
          for (int selectedRow : selectedRows) {
            final AddedFileInfo addedFileInfo = (AddedFileInfo)myTreeTable.getValueAt(selectedRow, 1);
            addedFileInfo.setIncluded(included);
            myModel.nodeChanged(addedFileInfo);
          }
        }
      }
    });
    tree.setCellRenderer(new AddedFileCellRenderer());
    TreeUtil.installActions(tree);
  }

  @Override
  protected JComponent createCenterPanel() {
    JPanel result = new JPanel(new BorderLayout());
    JComponent toolbar = createToolbar();
    result.add(toolbar, BorderLayout.NORTH);
    result.add(ScrollPaneFactory.createScrollPane(myTreeTable), BorderLayout.CENTER);
    return result;
  }

  private JComponent createToolbar() {
    ActionManager actionManager = ActionManager.getInstance();
    DefaultActionGroup group = new DefaultActionGroup();
    group.add(new SelectAllAction());
    group.add(new UnselectAllAction());
    return actionManager.createActionToolbar(CvsActionPlaces.ADD_FILES_TOOLBAR, group, true).getComponent();
  }

  private abstract class SelectUnselectAllAction extends AnAction {
    protected SelectUnselectAllAction(@NotNull Supplier<String> text, Icon icon) {
      super(text, Presentation.NULL_STRING, icon);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      for (AddedFileInfo addedFileInfo : getAllFileInfos()) {
        addedFileInfo.setIncluded(includedValue());
      }
      AddMultipleFilesOptionsDialog.this.myTreeTable.repaint();
    }

    private Collection<AddedFileInfo> getAllFileInfos() {
      ArrayList<AddedFileInfo> result = new ArrayList();
      for (AddedFileInfo myRoot : myRoots) {
        addChildrenTo(result, myRoot);
      }
      return result;
    }

    private void addChildrenTo(ArrayList<AddedFileInfo> result, AddedFileInfo addedFileInfo) {
      result.add(addedFileInfo);
      for (int i = 0; i < addedFileInfo.getChildCount(); i++) {
        addChildrenTo(result, (AddedFileInfo)addedFileInfo.getChildAt(i));
      }
    }

    protected abstract boolean includedValue();
  }

  private class SelectAllAction extends SelectUnselectAllAction {

    SelectAllAction() {
      super(CvsBundle.messagePointer("action.name.select.all"), AllIcons.Actions.Selectall);
    }

    @Override
    protected boolean includedValue() {
      return true;
    }
  }

  private class UnselectAllAction extends SelectUnselectAllAction {
    UnselectAllAction() {
      super(CvsBundle.messagePointer("action.name.unselect.all"), AllIcons.Actions.Unselectall);
    }

    @Override
    protected boolean includedValue() {
      return false;
    }
  }

}
