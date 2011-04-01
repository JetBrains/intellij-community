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

import com.intellij.ui.table.JBTable;

import javax.swing.*;
import javax.swing.table.TableModel;

/**
 * @author Konstantin Bulenkov
 */
public class DirDiffPanel {
  private JPanel myDiffPanel;
  private JBTable myTable;
  private JPanel myComponent;
  private JSplitPane mySplitPanel;
  private final TableModel myModel;

  public DirDiffPanel(TableModel model) {
    myModel = model;
  }

  private void createUIComponents() {
    myTable = new JBTable(myModel);
  }

  public JComponent getPanel() {
    return myComponent;
  }

  public JBTable getTable() {
    return myTable;
  }

  public JSplitPane getSplitPanel() {
    return mySplitPanel;
  }
}
