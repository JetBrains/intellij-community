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

import com.intellij.ide.diff.DirDiffSettings;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.table.JBTable;

import javax.swing.*;

/**
 * @author Konstantin Bulenkov
 */
public class DirDiffDialog extends DialogWrapper {
  private final DirDiffTableModel myModel;
  private final DirDiffSettings mySettings;
  private DirDiffPanel myDiffPanel;

  public DirDiffDialog(Project project, DirDiffTableModel model, DirDiffSettings settings) {
    super(project);
    setModal(false);
    myModel = model;
    mySettings = settings;
    setTitle("Directory Diff");
    init();
    final JBTable table = myDiffPanel.getTable();
    //final DirDiffElement element = myModel.getElementAt(0);
    //table.changeSelection(element.isSeparator() ? 1 : 0, 3, false, false);
    table.setColumnSelectionAllowed(false);
    table.getTableHeader().setReorderingAllowed(false);
    table.getTableHeader().setResizingAllowed(false);
    Disposer.register(getDisposable(), myModel);
  }

  @Override
  protected String getDimensionServiceKey() {
    setSize(800, 600);
    myDiffPanel.setupSplitter();
    return "DirDiffDialog";
  }

  @Override
  protected JComponent createCenterPanel() {
    myDiffPanel = new DirDiffPanel(myModel, this, mySettings);
    Disposer.register(getDisposable(), myDiffPanel);
    return myDiffPanel.getPanel();
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myDiffPanel.getTable();
  }
}
