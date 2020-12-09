// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.components;

import com.intellij.openapi.ui.Divider;
import com.intellij.openapi.ui.OnePixelDivider;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.ui.OnePixelSplitter;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.SideBorder;
import com.intellij.ui.hover.TableHoverListener;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.ui.table.JBTable;
import com.intellij.ui.tree.TreePathBackgroundSupplier;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.ui.treeStructure.treetable.TreeTableModel;
import com.intellij.ui.treeStructure.treetable.TreeTableModelAdapter;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.accessibility.AccessibleContextDelegate;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.accessibility.AccessibleContext;
import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.table.DefaultTableColumnModel;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.tree.TreeCellRenderer;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import static com.intellij.ui.render.RenderingUtil.FOCUSABLE_SIBLING;

/**
 * The tree-table view supports horizontal scrolling on a tree column only.
 *
 * Unlike {@link com.intellij.ui.treeStructure.treetable.TreeTable} this view holds
 * a tree and a table separately and transfer changes from the one to the other.
 *
 * Use {@link TreeTableModel} as an internal model. Please, do NOT use separate models to the tree or the table. It is considered as error.
 * Tree column of the tree-table has to be first and should return <code>TreeTableModel.class</code> from {@link TreeTableModel#getColumnClass(int)}.
 *
 * Cell renderers could be set separately or by calling {@link #setDefaultRenderer(Class, TableCellRenderer)}.
 */
@ApiStatus.Experimental
public class JBTreeTable extends JComponent implements TreePathBackgroundSupplier {

  private final Tree myTree;
  private final Table myTable;
  private final OnePixelSplitter split;

  private TreeTableModel myModel;

  private JTableHeader myTreeTableHeader;
  private float myColumnProportion = 0.1f;

  public JBTreeTable(@NotNull TreeTableModel model) {
    setLayout(new BorderLayout());

    myTree = new Tree() {
      @Override
      public void repaint(long tm, int x, int y, int width, int height) {
        if (!addTreeTableRowDirtyRegion(this, tm, x, y, width, height)) {
          super.repaint(tm, x, y, width, height);
        }
      }

      @Override
      public void treeDidChange() {
        super.treeDidChange();
        if (myTable != null) {
          myTable.revalidate();
          myTable.repaint();
        }
      }

      @Nullable
      @Override
      public Color getPathBackground(@NotNull TreePath path, int row) {
        return JBTreeTable.this.getPathBackground(path, row);
      }
    };
    myTable = new Table();
    myTree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
    myTree.setRootVisible(false);
    myTree.setBorder(JBUI.Borders.empty());
    myTable.setShowGrid(false);
    myTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    myTable.setColumnSelectionAllowed(false);
    myTable.getTableHeader().setReorderingAllowed(false);

    split = new OnePixelSplitter() {
      @Override
      protected Divider createDivider() {
        return new OnePixelDivider(isVertical(), this) {
          @Override
          public void paint(Graphics g) {
            final Rectangle bounds = g.getClipBounds();
            g.setColor(myTable.getShowVerticalLines() ? myTable.getGridColor() : myTable.getBackground());
            g.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);

            JTableHeader header = myTreeTableHeader;
            Rectangle rect = header.getHeaderRect(0);
            g.setClip(rect.x, rect.y, 2, rect.height);
            g.translate(-rect.width + 1, 0);
            header.paint(g);
          }
        };
      }
    };

    add(split);

    JScrollPane treePane = ScrollPaneFactory.createScrollPane(myTree, SideBorder.NONE);
    split.setFirstComponent(treePane);

    JScrollPane tablePane = ScrollPaneFactory.createScrollPane(myTable, SideBorder.NONE);
    split.setSecondComponent(tablePane);

    SelectionSupport selection = new SelectionSupport();

    treePane.setColumnHeaderView(myTreeTableHeader);
    treePane.getHorizontalScrollBar().addComponentListener(new ComponentAdapter() {

      {
        if (tablePane.isVisible()) {
          tablePane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_ALWAYS);
        }
      }

      @Override
      public void componentShown(ComponentEvent e) {
        tablePane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_ALWAYS);
      }

      @Override
      public void componentHidden(ComponentEvent e) {
        tablePane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
      }
    });
    int scrollMode = !SystemInfo.isMac ? JViewport.SIMPLE_SCROLL_MODE : JViewport.BLIT_SCROLL_MODE;
    treePane.getViewport().setScrollMode(scrollMode);
    tablePane.setVerticalScrollBar(treePane.getVerticalScrollBar());
    tablePane.getViewport().setScrollMode(scrollMode);

    myTree.getSelectionModel().addTreeSelectionListener(selection);
    myTable.getSelectionModel().addListSelectionListener(selection);
    myTable.setRowMargin(0);
    myTable.addMouseListener(selection);
    myTree.addPropertyChangeListener(JTree.ROW_HEIGHT_PROPERTY, evt -> {
      int treeRowHeight = myTree.getRowHeight();
      if (treeRowHeight == myTable.getRowHeight()) return;
      myTable.setRowHeight(treeRowHeight);
    });

    myTree.setCellRenderer(new TreeCellRenderer() {
      @Override
      public Component getTreeCellRendererComponent(JTree tree,
                                                    Object value,
                                                    boolean selected,
                                                    boolean expanded,
                                                    boolean leaf,
                                                    int row,
                                                    boolean hasFocus) {
        TreeColumnModel cm = (TreeColumnModel) myTreeTableHeader.getColumnModel();
        TableCellRenderer renderer = getDefaultRenderer(TreeTableModel.class);
        return renderer.getTableCellRendererComponent(myTable, value, selected, hasFocus, row, cm.treeColumnIndex);
      }
    });
    myTree.putClientProperty(FOCUSABLE_SIBLING, myTable);
    myTable.putClientProperty(FOCUSABLE_SIBLING, myTree);

    setModel(model);
  }

  @NotNull
  public Tree getTree() {
    return myTree;
  }

  @NotNull
  public JBTable getTable() {
    return myTable;
  }

  public void setDefaultRenderer(@NotNull Class<?> columnClass, @NotNull TableCellRenderer renderer) {
    myTable.setDefaultRenderer(columnClass,renderer);
  }

  @NotNull
  public TableCellRenderer getDefaultRenderer(@NotNull Class<?> columnClass) {
    return myTable.getDefaultRenderer(columnClass);
  }

  public void setModel(@NotNull TreeTableModel model) {
    myModel = model;

    myTree.setModel(model);
    myTable.setModel(new TreeTableModelAdapter(model, myTree, myTable));

    TreeColumnModel tcm = (TreeColumnModel) myTreeTableHeader.getColumnModel();
    if (tcm.treeColumnIndex >= 0) {
      myTable.removeColumn(myTable.getColumnModel().getColumn(tcm.treeColumnIndex));
    }

    if (myTree.getRowHeight() < 1) {
      myTable.setRowHeight(JBUIScale.scale(18));
    }
    else {
      myTable.setRowHeight(myTree.getRowHeight());
    }

    setColumnProportion(myColumnProportion);
  }

  public void setColumnProportion(float columnProportion) {
    myColumnProportion = columnProportion;
    split.setProportion(1f - ((myModel.getColumnCount() - 1) * columnProportion));
  }

  @SuppressWarnings("unused")
  public float getColumnProportion() {
    return myColumnProportion;
  }

  public TreeTableModel getModel() {
    return myModel;
  }

  @Nullable
  @Override
  public Color getPathBackground(@NotNull TreePath path, int row) {
    return null;
  }

  @Override
  public boolean hasFocus() {
    return myTree.hasFocus() || myTable.hasFocus();
  }

  @SuppressWarnings("unused")
  private boolean addTreeTableRowDirtyRegion(@NotNull JComponent component, long tm, int x, int y, int width, int height) {
    // checks if repaint manager should mark row of tree or table
    // and mark we need to repaint both components together,
    // otherwise super repaint
    boolean isNeedToRepaintRow = component.getWidth() == width;
    if (isNeedToRepaintRow) {
      repaint(tm, 0, y, this.getWidth(), height);
    }
    return isNeedToRepaintRow;
  }

  private class SelectionSupport extends MouseAdapter implements TreeSelectionListener, ListSelectionListener {

    @Override
    public void mouseClicked(MouseEvent e) {
      if (e.getClickCount() == 2 && e.getSource() == myTable) {
        int row = myTable.getSelectedRow();
        if (myTree.isCollapsed(row)) {
          myTree.expandRow(row);
        } else {
          myTree.collapseRow(row);
        }
      }
    }

    @Override
    public void valueChanged(ListSelectionEvent e) {
      if (e.getSource() == myTable.getSelectionModel()) {
        int row = myTable.getSelectedRow();
        if (row >= 0) {
          myTree.setSelectionRow(row);
        }
      }
    }

    @Override
    public void valueChanged(TreeSelectionEvent e) {
      if (e.getSource() == myTree.getSelectionModel()) {
        int row = myTree.getRowForPath(myTree.getSelectionPath());
        if (row >= 0) {
          myTable.setRowSelectionInterval(row, row);
        } else {
          myTable.clearSelection();
        }
      }
    }
  }

  private final class Table extends JBTable {
    final JBTable ref = new JBTable();

    private Table() {
      myTreeTableHeader = new JBTableHeader() {
        @Override
        public AccessibleContext getAccessibleContext() {
          return new MyAccessibleContext();
        }

        @Override
        public int getWidth() {
          return super.getWidth() + 1;
        }
      };
      myTreeTableHeader.setTable(ref); // <- we steal table header and need to provide any JTable to handle right ui painting
      myTreeTableHeader.setColumnModel(new TreeColumnModel());
      myTreeTableHeader.setReorderingAllowed(false);
      myTreeTableHeader.setResizingAllowed(false);

      // do not paint hover for table row separately from tree
      TableHoverListener.DEFAULT.removeFrom(this);
    }

    @Override
    public void setRowHeight(int rowHeight) {
      super.setRowHeight(rowHeight);
      if (myTree != null && myTree.getRowHeight() < rowHeight) {
        myTree.setRowHeight(getRowHeight());
      }
    }

    @Override
    public void updateUI() {
      super.updateUI();
      // dynamically update ui for stolen header
      if (ref != null) {
        ref.updateUI();
      }
    }

    private final class MyAccessibleContext extends AccessibleContextDelegate {

      MyAccessibleContext() {
        super(myTable.getAccessibleContext());
      }

      @Override
      protected Container getDelegateParent() {
        return JBTreeTable.this;
      }
    }
  }

  private final class TreeColumnModel extends DefaultTableColumnModel {

    private int treeColumnIndex = -1;

    private TreeColumnModel() {
      addColumn(new TableColumn(0) {

        @Override
        public int getWidth() {
          return getTotalColumnWidth();
        }

        @Override
        public Object getHeaderValue() {
          return treeColumnIndex < 0 ? " " : ((TreeTableModel) myTree.getModel()).getColumnName(treeColumnIndex);
        }
      });
      addColumn(new TableColumn(1, 0));
      myTree.addPropertyChangeListener(JTree.TREE_MODEL_PROPERTY, evt -> {
        TreeTableModel model = (TreeTableModel) myTree.getModel();
        treeColumnIndex = -1;
        for (int i = 0; i < model.getColumnCount(); i++) {
          if (TreeTableModel.class.isAssignableFrom(model.getColumnClass(i))) {
            if (i != 0) throw new IllegalArgumentException("Tree column must be first");
            treeColumnIndex = i;
            break;
          }
        }
      });
    }

    @Override
    public int getTotalColumnWidth() {
      return myTree.getVisibleRect().width + 1;
    }
  }
}
