package com.intellij.openapi.diff.impl.highlighting;

import com.intellij.openapi.diff.ex.DiffFragment;

import java.util.ArrayList;
import java.util.List;

class List2D {
  private final ArrayList<List> myRows = new ArrayList<List>();
  private ArrayList myCurrentRow = null;

  public void add(DiffFragment element) {
    ensureRowExists();
    myCurrentRow.add(element);
  }

  private void ensureRowExists() {
    if (myCurrentRow == null) {
      myCurrentRow = new ArrayList();
      myRows.add(myCurrentRow);
    }
  }

  public void newRow() {
    myCurrentRow = null;
  }

  //
  public DiffFragment[][] toArray() {

    DiffFragment[][] result = new DiffFragment[myRows.size()][];
    for (int i = 0; i < result.length; i++) {
      List row = myRows.get(i);
      result[i] = new DiffFragment[row.size()];
      System.arraycopy(row.toArray(), 0, result[i], 0, row.size());
    }
    return result;
  }

  public void addAll(DiffFragment[] line) {
    ensureRowExists();
    for (int i = 0; i < line.length; i++) {
      DiffFragment value = line[i];
      myCurrentRow.add(value);
    }
  }
}
