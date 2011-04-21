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

import com.intellij.ide.diff.DiffElement;
import com.intellij.ide.diff.DirDiffSettings;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.diff.impl.dir.actions.DirDiffToolbarActions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.table.JBTable;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;

/**
 * @author Konstantin Bulenkov
 */
public class DirDiffPanel {
  private JPanel myDiffPanel;
  private JBTable myTable;
  private JPanel myComponent;
  private JSplitPane mySplitPanel;
  private TextFieldWithBrowseButton mySourceDirField;
  private TextFieldWithBrowseButton myTargetDirField;
  private JBLabel myTargetDirLabel;
  private JBLabel mySourceDirLabel;
  private JPanel myActionsPanel;
  private JPanel myActionsCenterPanel;
  private JComboBox myFileFilter;
  private JPanel myToolBarPanel;
  private JBScrollPane myScrollPane;
  private final DirDiffTableModel myModel;
  public JLabel myErrorLabel;
  private final DirDiffDialog myDialog;
  private JComponent myDiffPanelComponent;
  private JComponent myViewComponent;
  private DiffElement myCurrentElement;

  public DirDiffPanel(DirDiffTableModel model, DirDiffDialog dirDiffDialog, DirDiffSettings settings) {
    mySplitPanel.setDividerLocation(0.5);
    myModel = model;
    myDialog = dirDiffDialog;
    mySourceDirField.setText(model.getSourceDir().getPath());
    myTargetDirField.setText(model.getTargetDir().getPath());
    mySourceDirLabel.setIcon(model.getSourceDir().getIcon());
    myTargetDirLabel.setIcon(model.getTargetDir().getIcon());
    myTable.setModel(myModel);
    final DirDiffTableCellRenderer renderer = new DirDiffTableCellRenderer(myTable);
    myTable.setDefaultRenderer(Object.class, renderer);
    myTable.getSelectionModel().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
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
          if (element == null) return;
          final Project project = myModel.getProject();
          clearDiffPanel();
          if (element.getType() == DType.CHANGED) {
            myDiffPanelComponent = element.getSource().getDiffComponent(element.getTarget(), project, myDialog.getWindow());
            if (myDiffPanelComponent != null) {
              myDiffPanel.add(myDiffPanelComponent, BorderLayout.CENTER);
              myCurrentElement = element.getSource();
            }

          } else {
            final DiffElement object = element.isSource() ? element.getSource() : element.getTarget();
            myViewComponent = object.getViewComponent(project);

            if (myViewComponent != null) {
              myCurrentElement = object;
              myDiffPanel.add(myViewComponent, BorderLayout.CENTER);
              myViewComponent.revalidate();
            } else {
              myDiffPanel.add(getErrorLabel(), BorderLayout.CENTER);
              myDiffPanel.revalidate();
              myDiffPanel.repaint();
            }
          }
        }
        myDialog.setTitle(myModel.getTitle());
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
        } else if (keyCode == KeyEvent.VK_UP && row != 0) {
          row--;
          final DirDiffElement element = myModel.getElementAt(row);
          if (element == null) return;
          if (element.isSeparator()) {
            row--;
          }
        } else {
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
    final ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar("DirDiff", new DirDiffToolbarActions(myModel), true);
    myToolBarPanel.add(toolbar.getComponent(), BorderLayout.CENTER);
  }

  private JLabel getErrorLabel() {
    return myErrorLabel == null ? myErrorLabel = new JLabel("Can't recognize file type", SwingConstants.CENTER) : myErrorLabel;
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
  }

  public JComponent getPanel() {
    return myComponent;
  }

  public JBTable getTable() {
    return myTable;
  }

  public void dispose() {
    clearDiffPanel();
  }
}
