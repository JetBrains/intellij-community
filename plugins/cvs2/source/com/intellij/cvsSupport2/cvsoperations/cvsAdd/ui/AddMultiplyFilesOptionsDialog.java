package com.intellij.cvsSupport2.cvsoperations.cvsAdd.ui;

import com.intellij.cvsSupport2.CvsActionPlaces;
import com.intellij.cvsSupport2.cvsoperations.cvsAdd.AddedFileInfo;
import com.intellij.cvsSupport2.keywordSubstitution.KeywordSubstitutionWrapper;
import com.intellij.cvsSupport2.ui.Options;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.peer.PeerFactory;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.dualView.TreeTableView;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.ComboBoxTableCellEditor;
import com.intellij.util.ui.ComboBoxTableCellRenderer;
import com.intellij.util.ui.tree.TreeUtil;
import com.intellij.util.ui.treetable.ListTreeTableModelOnColumns;
import com.intellij.util.ui.treetable.TreeTable;
import com.intellij.util.ui.treetable.TreeTableModel;
import com.intellij.CvsBundle;

import javax.swing.*;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.MutableTreeNode;
import java.awt.*;
import java.util.*;

/**
 * author: lesya
 */


public class AddMultiplyFilesOptionsDialog extends AbstractAddOptionsDialog {
  private final Collection<AddedFileInfo> myRoots;
  private final static JCheckBox CHECKBOX = new JCheckBox();

  private final ColumnInfo INCLUDED = new ColumnInfo("") {

    public Object valueOf(Object object) {
      return Boolean.valueOf(((AddedFileInfo)object).included());
    }

    public Class getColumnClass() {
      return Boolean.class;
    }

    public boolean isCellEditable(Object o) {
      return true;
    }

    public void setValue(Object o, Object aValue) {
      ((AddedFileInfo)o).setIncluded(((Boolean)aValue).booleanValue());
      myTreeTable.repaint();
    }

    public int getWidth(JTable table) {
      return CHECKBOX.getPreferredSize().width + 4;
    }
  };


  private static final ColumnInfo FILE = new ColumnInfo(CvsBundle.message("add.multiple.files.file.column.name")) {
    public Object valueOf(Object object) {
      return ((AddedFileInfo)object).getPresentableText();
    }

    public Class getColumnClass() {
      return TreeTableModel.class;
    }

    public boolean isCellEditable(Object o) {
      return true;
    }
  };

  private static final ColumnInfo KEYWORD_SUBSTITUTION = new ColumnInfo(
    CvsBundle.message("add.multiple.files.keyword.substitution.column.name")) {
    public Object valueOf(Object object) {
      return ((AddedFileInfo)object).getKeywordSubstitutionsWithSelection();
    }

    public boolean isCellEditable(Object o) {
      AddedFileInfo addedFileInfo = (AddedFileInfo)o;
      if (addedFileInfo.getFile().isDirectory()) return false;
      return addedFileInfo.included();
    }

    public void setValue(Object o, Object aValue) {
      ((AddedFileInfo)o).setKeywordSubstitution(((KeywordSubstitutionWrapper)aValue).getSubstitution());
    }

    public TableCellRenderer getRenderer(Object o) {
      AddedFileInfo addedFileInfo = (AddedFileInfo)o;
      if (addedFileInfo.getFile().isDirectory()) return TABLE_CELL_RENDERER;
      return ComboBoxTableCellRenderer.INSTANCE;
    }

    public TableCellEditor getEditor(Object item) {
      return ComboBoxTableCellEditor.INSTANCE;
    }

    public int getWidth(JTable table) {
      return table.getFontMetrics(table.getFont()).stringWidth(getName()) + 10;
    }
  };

  private final ColumnInfo[] COLUMNS = new ColumnInfo[]{INCLUDED, FILE, KEYWORD_SUBSTITUTION};

  private TreeTable myTreeTable;
  private ListTreeTableModelOnColumns myModel;
  private static final JPanel J_PANEL = new JPanel();
  private static final TableCellRenderer TABLE_CELL_RENDERER = new TableCellRenderer() {
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
  private Observer myObserver;


  public AddMultiplyFilesOptionsDialog(Project project,
                                       Collection<AddedFileInfo> roots,
                                       Options options) {
    super(project, options);

    myRoots = roots;
    myObserver = new Observer() {
          public void update(Observable o, Object arg) {
            setOKButtonEnabling();
          }
        };

    for (Iterator each = myRoots.iterator(); each.hasNext();) {
      ((AddedFileInfo)each.next()).addIncludedObserver(myObserver);
    }

    setTitle(com.intellij.CvsBundle.message("dialog.title.add.files.to.cvs"));

    createTree();

    expandAll();

    init();

    setOKButtonEnabling();
  }

  private void setOKButtonEnabling() {
    setOKActionEnabled(hasIncludedNodes());
  }

  private boolean hasIncludedNodes() {
    for (Iterator iterator = myRoots.iterator(); iterator.hasNext();) {
      AddedFileInfo addedFileInfo = (AddedFileInfo)iterator.next();
      if (addedFileInfo.hasIncludedNodes()) return true;
    }
    return false;
  }

  public void dispose() {
    super.dispose();
    for (Iterator each = myRoots.iterator(); each.hasNext();) {
      ((AddedFileInfo)each.next()).removeIncludedObserver(myObserver);
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

    for (Iterator each = myRoots.iterator(); each.hasNext();) {
      root.add((MutableTreeNode)each.next());
    }

    myModel = new ListTreeTableModelOnColumns(root, COLUMNS);

    myTreeTable = new TreeTableView(myModel);

    int comboHeight = new JComboBox().getPreferredSize().height;
    int checkBoxHeight = new JCheckBox().getPreferredSize().height;
    myTreeTable.setMinRowHeight(Math.max(comboHeight, checkBoxHeight) + 2);

    myTreeTable.setRootVisible(false);

    myTreeTable.getTree().setCellRenderer(new AddedFileCellRenderer());
    PeerFactory.getInstance().getUIHelper().installToolTipHandler(myTreeTable);
    TreeUtil.installActions(myTreeTable.getTree());
  }

  protected JComponent createCenterPanel() {
    JPanel result = new JPanel(new BorderLayout());
    JComponent toolbal = createToolbal();
    result.add(toolbal, BorderLayout.NORTH);
    result.add(ScrollPaneFactory.createScrollPane(myTreeTable), BorderLayout.CENTER);
    return result;
  }

  private JComponent createToolbal() {
    ActionManager actionManager = ActionManager.getInstance();
    DefaultActionGroup group = new DefaultActionGroup();
    group.add(new SelectAllAction());
    group.add(new UnselectAllAction());
    return actionManager.createActionToolbar(CvsActionPlaces.ADD_FILES_TOOLBAR, group, true).getComponent();
  }

  private abstract class SelectUnselectAllAction extends AnAction {
    protected SelectUnselectAllAction(String text, Icon icon) {
      super(text, null, icon);
    }

    public void actionPerformed(AnActionEvent e) {
      for (Iterator iterator = getAllFileInfos().iterator(); iterator.hasNext();) {
        ((AddedFileInfo)iterator.next()).setIncluded(includedValue());
      }

      AddMultiplyFilesOptionsDialog.this.myTreeTable.repaint();
    }

    private Collection getAllFileInfos() {
      ArrayList result = new ArrayList();
      for (Iterator iterator = myRoots.iterator(); iterator.hasNext();) {
        addChildrenTo(result, (AddedFileInfo)iterator.next());
      }
      return result;
    }

    private void addChildrenTo(ArrayList result, AddedFileInfo addedFileInfo) {
      result.add(addedFileInfo);
      for (int i = 0; i < addedFileInfo.getChildCount(); i++) {
        addChildrenTo(result, (AddedFileInfo)addedFileInfo.getChildAt(i));
      }
    }

    protected abstract boolean includedValue();
  }

  private class SelectAllAction extends SelectUnselectAllAction {

    public SelectAllAction() {
      super(com.intellij.CvsBundle.message("action.name.select.all"), IconLoader.getIcon("/actions/selectall.png"));
    }

    protected boolean includedValue() {
      return true;
    }
  }

  private class UnselectAllAction extends SelectUnselectAllAction {
    public UnselectAllAction() {
      super(com.intellij.CvsBundle.message("action.name.unselect.all"), IconLoader.getIcon("/actions/unselectall.png"));
    }

    protected boolean includedValue() {
      return false;
    }
  }

}
