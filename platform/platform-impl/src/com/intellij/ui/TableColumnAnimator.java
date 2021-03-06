// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
public final class TableColumnAnimator extends Timer implements ActionListener {
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
