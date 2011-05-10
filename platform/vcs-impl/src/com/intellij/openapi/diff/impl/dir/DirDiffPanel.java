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
package com.intellij.openapi.diff.impl.dir;

import com.intellij.ide.DataManager;
import com.intellij.ide.diff.DiffElement;
import com.intellij.ide.diff.DirDiffSettings;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.diff.impl.dir.actions.DirDiffToolbarActions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBLoadingPanel;
import com.intellij.ui.table.JBTable;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;
import java.awt.*;
import java.awt.event.*;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author Konstantin Bulenkov
 */
@SuppressWarnings({"unchecked"})
public class DirDiffPanel implements Disposable {
  public static final String DIVIDER_PROPERTY = "dir.diff.panel.divider.location";
  private JPanel myDiffPanel;
  private JBTable myTable;
  private JPanel myComponent;
  private JSplitPane mySplitPanel;
  private TextFieldWithBrowseButton mySourceDirField;
  private TextFieldWithBrowseButton myTargetDirField;
  private JBLabel myTargetDirLabel;
  private JBLabel mySourceDirLabel;
  private JPanel myToolBarPanel;
  private JPanel myRootPanel;
  private JTextField myFilter;
  private final DirDiffTableModel myModel;
  public JLabel myErrorLabel;
  private final DirDiffWindow myDiffWindow;
  private JComponent myDiffPanelComponent;
  private JComponent myViewComponent;
  private DiffElement myCurrentElement;
  private String oldFilter;

  public DirDiffPanel(DirDiffTableModel model, DirDiffWindow wnd, DirDiffSettings settings) {
    myModel = model;
    myDiffWindow = wnd;
    mySourceDirField.setText(model.getSourceDir().getPath());
    myTargetDirField.setText(model.getTargetDir().getPath());
    mySourceDirLabel.setIcon(model.getSourceDir().getIcon());
    myTargetDirLabel.setIcon(model.getTargetDir().getIcon());
    myModel.setTable(myTable);
    myModel.setPanel(this);
    Disposer.register(this, myModel);
    myTable.setModel(myModel);

    final DirDiffTableCellRenderer renderer = new DirDiffTableCellRenderer(myTable);
    myTable.setDefaultRenderer(Object.class, renderer);
    myTable.getSelectionModel().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    final Project project = myModel.getProject();
    myTable.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
      @Override
      public void valueChanged(ListSelectionEvent e) {
        final DirDiffElement last = myModel.getElementAt(e.getLastIndex());
        final DirDiffElement first = myModel.getElementAt(e.getFirstIndex());
        if (last == null || first == null) return;
        if (last.isSeparator()) {
          myTable.getSelectionModel().setLeadSelectionIndex(e.getFirstIndex());
        }
        else if (first.isSeparator()) {
          myTable.getSelectionModel().setLeadSelectionIndex(e.getLastIndex());
        }
        else {
          final DirDiffElement element = myModel.getElementAt(myTable.getSelectedRow());
          if (element == null
              || (myCurrentElement != null && (myCurrentElement == element.getSource() || myCurrentElement == element.getTarget()))) {
            return;
          }
          clearDiffPanel();
          if (element.getType() == DType.CHANGED) {
            myDiffPanelComponent = element.getSource().getDiffComponent(element.getTarget(), project, myDiffWindow.getWindow());
            if (myDiffPanelComponent != null) {
              myDiffPanel.add(myDiffPanelComponent, BorderLayout.CENTER);
              myCurrentElement = element.getSource();
            } else {
              myDiffPanel.add(getErrorLabel(), BorderLayout.CENTER);
              myDiffPanel.revalidate();
              myDiffPanel.repaint();
            }
          } else {
            final DiffElement object;
            if (element.getType() == DType.ERROR) {
              object = element.getSource() == null ? element.getTarget() : element.getSource();
            } else {
              object = element.isSource() ? element.getSource() : element.getTarget();
            }
            myViewComponent = object.getViewComponent(project, null);

            if (myViewComponent != null) {
              myCurrentElement = object;
              myDiffPanel.add(myViewComponent, BorderLayout.CENTER);
              DataManager.registerDataProvider(myDiffPanel, myCurrentElement.getDataProvider(project));
              myDiffPanel.revalidate();
              myDiffPanel.repaint();
            } else {
              myDiffPanel.add(getErrorLabel(), BorderLayout.CENTER);
              myDiffPanel.revalidate();
              myDiffPanel.repaint();
            }
          }
        }
        myDiffWindow.setTitle(myModel.getTitle());
      }
    });
    myTable.addKeyListener(new KeyAdapter() {
      @Override
      public void keyPressed(KeyEvent e) {
        final int keyCode = e.getKeyCode();
        final int rows = myTable.getRowCount();
        int row = myTable.getSelectedRow();
        if (keyCode == KeyEvent.VK_DOWN && row != rows - 1) {
          row++;
          final DirDiffElement element = myModel.getElementAt(row);
          if (element == null) return;
          if (element.isSeparator()) {
            row++;
          }
        }
        else if (keyCode == KeyEvent.VK_UP && row != 0) {
          row--;
          final DirDiffElement element = myModel.getElementAt(row);
          if (element == null) return;
          if (element.isSeparator()) {
            row--;
          }
        }
        else {
          return;
        }
        final DirDiffElement element = myModel.getElementAt(row);
        if (element == null) return;
        if (!element.isSeparator()) {
          e.consume();
          myTable.changeSelection(row, (myModel.getColumnCount() - 1) / 2, false, false);
        }
      }
    });
    final TableColumnModel columnModel = myTable.getColumnModel();
    final TableColumn operationColumn = columnModel.getColumn((columnModel.getColumnCount() - 1) / 2);
    operationColumn.setMaxWidth(25);
    operationColumn.setMinWidth(25);
    for (int i = 0; i < columnModel.getColumnCount(); i++) {
      final String name = myModel.getColumnName(i);
      final TableColumn column = columnModel.getColumn(i);
      if (DirDiffTableModel.COLUMN_DATE.equals(name)) {
        column.setMaxWidth(90);
        column.setMinWidth(90);
      } else if (DirDiffTableModel.COLUMN_SIZE.equals(name)) {
        column.setMaxWidth(120);
        column.setMinWidth(120);
      }
    }
    final ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar("DirDiff", new DirDiffToolbarActions(myModel), true);
    myToolBarPanel.add(toolbar.getComponent(), BorderLayout.CENTER);
    final JBLoadingPanel loadingPanel = new JBLoadingPanel(new BorderLayout(), wnd.getDisposable());
    loadingPanel.add(myComponent, BorderLayout.CENTER);
    myTable.putClientProperty(myModel.DECORATOR, loadingPanel);
    myTable.addComponentListener(new ComponentAdapter() {
      @Override
      public void componentShown(ComponentEvent e) {
        myTable.removeComponentListener(this);
        myModel.reloadModel();
      }
    });
    myRootPanel.removeAll();
    myRootPanel.add(loadingPanel, BorderLayout.CENTER);
    myModel.addModelListener(new DirDiffModelListener() {
      @Override
      public void updateStarted() {
        myFilter.setEnabled(false);
      }

      @Override
      public void updateFinished() {
        myFilter.setEnabled(true);
      }
    });

    myFilter.setText(settings.getFilter());
    oldFilter = myFilter.getText();
    myFilter.addKeyListener(new KeyAdapter() {
      @Override
      public void keyPressed(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_ENTER) {
          e.consume();
          fireFilterUpdated();
        }
        else if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
          e.consume();
          focusTable();
        }
      }
    });
    final Callable<DiffElement> srcChooser = myModel.getSourceDir().getElementChooser(project);
    final Callable<DiffElement> trgChooser = myModel.getTargetDir().getElementChooser(project);
    if (srcChooser != null) {
      mySourceDirField.setButtonEnabled(true);
      mySourceDirField.addActionListener(new AbstractAction() {
        @Override
        public void actionPerformed(ActionEvent e) {
          try {
            final Callable<DiffElement> chooser = myModel.getSourceDir().getElementChooser(project);
            if (chooser == null) return;
            final DiffElement newElement = chooser.call();
            if (newElement != null) {
              myModel.setSourceDir(newElement);
              mySourceDirField.setText(newElement.getPath());
            }
          } catch (Exception e1) {//
          }
        }
      });
    } else {
      mySourceDirField.setButtonEnabled(false);
      mySourceDirField.getButton().setVisible(false);
      mySourceDirField.setEditable(false);
    }

    if (trgChooser != null) {
      myTargetDirField.setButtonEnabled(true);
      myTargetDirField.addActionListener(new AbstractAction() {
        @Override
        public void actionPerformed(ActionEvent e) {
          try {
            final Callable<DiffElement> chooser = myModel.getTargetDir().getElementChooser(project);
            if (chooser == null) return;
            final DiffElement newElement = chooser.call();
            if (newElement != null) {
              myModel.setTargetDir(newElement);
              myTargetDirField.setText(newElement.getPath());
            }
          } catch (Exception e1) {//
          }
        }
      });
    } else {
      myTargetDirField.setButtonEnabled(false);
      myTargetDirField.getButton().setVisible(false);
      myTargetDirField.setEditable(false);
    }
  }

  public void focusTable() {
    final IdeFocusManager focusManager = myModel.getProject().isDefault()
                                         ? IdeFocusManager.getGlobalInstance() : IdeFocusManager.getInstance(myModel.getProject());
    focusManager.doWhenFocusSettlesDown(new Runnable() {
      @Override
      public void run() {
        focusManager.requestFocus(myTable, true);
      }
    });
  }

  public JTextField getFilter() {
    return myFilter;
  }

  private void fireFilterUpdated() {
    final String newFilter = myFilter.getText();
    if (!StringUtil.equals(oldFilter, newFilter)) {
      oldFilter = newFilter;
      myModel.getSettings().setFilter(newFilter);
      myModel.applySettings();
    }
  }

  private JLabel getErrorLabel() {
    return myErrorLabel == null ? myErrorLabel = new JLabel("Unknown or binary file type", SwingConstants.CENTER) : myErrorLabel;
  }

  private void clearDiffPanel() {
    if (myDiffPanelComponent != null) {
      myDiffPanel.remove(myDiffPanelComponent);
      myDiffPanelComponent = null;
      if (myCurrentElement != null) {
        myCurrentElement.disposeDiffComponent();
      }
    }
    if (myViewComponent != null) {
      myDiffPanel.remove(myViewComponent);
      myViewComponent = null;
      if (myCurrentElement != null) {
        myCurrentElement.disposeViewComponent();
      }
    }
    myCurrentElement = null;
    myDiffPanel.remove(getErrorLabel());
    DataManager.removeDataProvider(myDiffPanel);
  }

  public JComponent getPanel() {
    return myRootPanel;
  }

  public JBTable getTable() {
    return myTable;
  }

  public void dispose() {
    myModel.stopUpdating();
    PropertiesComponent.getInstance().setValue(DIVIDER_PROPERTY, String.valueOf(mySplitPanel.getDividerLocation()));
    clearDiffPanel();
  }

  private void createUIComponents() {
    final AtomicBoolean callUpdate = new AtomicBoolean(true);
    myRootPanel = new JPanel(new BorderLayout()) {
      @Override
      protected void paintChildren(Graphics g) {
        super.paintChildren(g);
        if (callUpdate.get()) {
          callUpdate.set(false);
          myModel.reloadModel();
        }
      }
    };
  }

  public void setupSplitter() {
    mySplitPanel.setDividerLocation(Integer.valueOf(PropertiesComponent.getInstance().getValue(DIVIDER_PROPERTY, "200")));
  }
}
