/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
