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
package com.intellij.ui;

import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import javax.swing.table.TableColumn;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Konstantin Bulenkov
 */
public class TableColumnAnimator extends Timer implements ActionListener {
  private final JTable myTable;
  private boolean added = false;
  private final List<Pair<TableColumn, Integer>> myColumns = new ArrayList<>();
  private int myStep = 30;
  private Runnable myDone;

  public TableColumnAnimator(JTable table) {
    super(50, null);
    myTable = table;
    addActionListener(this);
  }

  public void addColumn(TableColumn column, int preferredWidth) {
    myColumns.add(Pair.create(column, preferredWidth));
  }

  public void setStep(int step) {
    myStep = step;
  }

  @Override
  public void actionPerformed(ActionEvent e) {
    if (myColumns.isEmpty()) {
      stop();
      if (myDone != null) {
        SwingUtilities.invokeLater(myDone);
      }
      return;
    }

    final TableColumn c = myColumns.get(0).first;
    if (!added) {
      myTable.addColumn(c);
      c.setMaxWidth(0);
      c.setPreferredWidth(0);
      c.setWidth(0);
      added = true;
    }

    final int prefWidth = myColumns.get(0).second.intValue();
    int width = c.getWidth();
    width = Math.min(width + myStep, prefWidth);
    c.setMaxWidth(width);
    c.setPreferredWidth(width);
    c.setWidth(width);

    if (width == prefWidth) {
      added = false;
      myColumns.remove(0);
      //c.setMaxWidth(oldMaxWidth);
    }
  }

  public void startAndDoWhenDone(Runnable done) {
    myDone = done;
  }

  @NonNls
  @Override
  public String toString() {
    return "Table column animator";
  }
}
