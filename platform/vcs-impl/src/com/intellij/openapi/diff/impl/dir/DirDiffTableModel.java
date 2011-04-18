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
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.Nullable;

import javax.swing.table.AbstractTableModel;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Konstantin Bulenkov
 */
public class DirDiffTableModel extends AbstractTableModel {
  public static final String COLUMN_NAME = "Name";
  public static final String COLUMN_SIZE = "Size";
  public static final String COLUMN_DATE = "Date";
  private final Project myProject;
  private final DirDiffSettings mySettings;
  private DiffElement mySrc;
  private DiffElement myTrg;
  final List<DirDiffElement> myElements = new ArrayList<DirDiffElement>();
  private boolean myUpdating = false;

  public DirDiffTableModel(Project project, DiffElement src, DiffElement trg, ProgressIndicator indicator, DirDiffSettings settings) {
    myProject = project;
    mySettings = settings;
    mySrc = src;
    myTrg = trg;
    reloadModel(indicator);
  }

  public void reloadModel(ProgressIndicator indicator) {
    myUpdating = true;
    clear();
    final DTree tree = new DTree(null, "", true);
    scan(mySrc, tree, true);
    scan(myTrg, tree, false);

    tree.setSource(mySrc);
    tree.setTarget(myTrg);
    tree.update(mySettings);
    tree.updateVisibility(mySettings);

    myElements.clear();
    fillElements(tree);
    fireTableDataChanged();
    myUpdating = false;
  }

  private void fillElements(DTree tree) {
    boolean separatorAdded = tree.getParent() == null;
    for (DTree child : tree.getChildren()) {
      if (!child.isContainer()) {
        if (child.isVisible()) {
          if (!separatorAdded) {
            myElements.add(DirDiffElement.createDirElement(tree.getSource(), tree.getTarget(), tree.getPath()));
            separatorAdded = true;
          }
          switch (child.getType()) {
            case SOURCE:
              myElements.add(DirDiffElement.createSourceOnly(child.getSource()));
              break;
            case TARGET:
              myElements.add(DirDiffElement.createTargetOnly(child.getTarget()));
              break;
            case CHANGED:
              myElements.add(DirDiffElement.createChange(child.getSource(), child.getTarget()));
              break;
            case EQUAL:
              myElements.add(DirDiffElement.createEqual(child.getSource(), child.getTarget()));
              break;
          }
        }
      } else {
        fillElements(child);
      }
    }
  }

  public void clear() {
    if (!myElements.isEmpty()) {
      final int size = myElements.size();
      myElements.clear();
      fireTableRowsDeleted(0, size - 1);
    }
  }

  private static void scan(DiffElement element, DTree root, boolean source) {
    if (element.isContainer()) {
      try {
        for (DiffElement child : element.getChildren()) {
          scan(child, root.addChild(child, source), source);
        }
      }
      catch (IOException e) {//
      }
    }
  }

  public String getTitle() {
    return "Diff for " + mySrc.getPath() + " and " + myTrg.getPath();
  }

  public DirDiffElement getElementAt(int index) {
    return 0 <= index && index < myElements.size() ? myElements.get(index) : null;
  }

  public DiffElement getSourceDir() {
    return mySrc;
  }

  public DiffElement getTargetDir() {
    return myTrg;
  }

  @Override
  public int getRowCount() {
    return myElements.size();
  }

  @Override
  public int getColumnCount() {
    int count = 3;
    if (mySettings.showDate) count += 2;
    if (mySettings.showSize) count += 2;
    return count;
  }

  @Nullable
  @Override
  public Object getValueAt(int rowIndex, int columnIndex) {
    final DirDiffElement element = myElements.get(rowIndex);
    if (element.isSeparator()) {
      return columnIndex == 0 ? element.getName() : null;
    }

    final String name = getColumnName(columnIndex);
    boolean isSrc = columnIndex < getColumnCount() / 2;
    if (name.equals(COLUMN_NAME)) {
      return isSrc ? element.getSourceName() : element.getTargetName();
    } else if (name.equals(COLUMN_SIZE)) {
      return isSrc ? element.getSourceSize() : element.getTargetSize();
    } else  if (name.equals(COLUMN_DATE)) {
      return isSrc ? element.getSourceModificationDate() : element.getTargetModificationDate();
    }
    return "";
  }

  @Override
  public String getColumnName(int column) {
    final int count = (getColumnCount() - 1) / 2;
    if (column == count) return "*";
    if (column > count) {
      column = getColumnCount() - 1 - column;
    }
    switch (column) {
      case 0: return COLUMN_NAME;
      case 1: return mySettings.showSize ? COLUMN_SIZE : COLUMN_DATE;
      case 2: return COLUMN_DATE;
    }
    return "";
  }

  public Project getProject() {
    return myProject;
  }

  public boolean isShowEqual() {
    return mySettings.showEqual;
  }

  public void setShowEqual(boolean show) {
    mySettings.showEqual = show;
  }

  public boolean isShowDifferent() {
    return mySettings.showDifferent;
  }

  public void setShowDifferent(boolean show) {
    mySettings.showDifferent = show;
  }

  public boolean isShowNewOnSource() {
    return mySettings.showNewOnSource;
  }

  public void setShowNewOnSource(boolean show) {
    mySettings.showNewOnSource = show;
  }

  public boolean isShowNewOnTarget() {
    return mySettings.showNewOnTarget;
  }

  public void setShowNewOnTarget(boolean show) {
    mySettings.showNewOnTarget = show;
  }

  public boolean isUpdating() {
    return myUpdating;
  }
}
