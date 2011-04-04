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

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;

import javax.swing.*;

/**
 * @author Konstantin Bulenkov
 */
public class DirDiffDialog extends DialogWrapper {
  private final DirDiffTableModel myModel;
  private DirDiffPanel myDiffPanel;

  public DirDiffDialog(Project project, DirDiffTableModel model) {
    super(project);
    myModel = model;
    setSize(600, 600);
    setTitle("Directory Diff");
    init();
    myDiffPanel.getTable().changeSelection(myModel.getElementAt(0).isSeparator() ? 1: 0, 3, false, false);
  }

  @Override
  protected String getDimensionServiceKey() {
    return "DirDiffDialog";
  }

  @Override
  protected JComponent createCenterPanel() {
    myDiffPanel = new DirDiffPanel(myModel, this);
    return myDiffPanel.getPanel();
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myDiffPanel.getTable();
  }

  @Override
  public void doCancelAction() {
    super.doCancelAction();
    myDiffPanel.dispose();
  }
}
