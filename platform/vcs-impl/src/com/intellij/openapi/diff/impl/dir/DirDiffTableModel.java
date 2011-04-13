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
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.util.containers.HashSet;
import org.jetbrains.annotations.Nullable;

import javax.swing.table.AbstractTableModel;
import java.io.IOException;
import java.util.*;

/**
 * @author Konstantin Bulenkov
 */
public class DirDiffTableModel extends AbstractTableModel {
  private final Project myProject;
  private DiffElement mySrc;
  private DiffElement myTrg;
  final List<DirDiffElement> myElements = new ArrayList<DirDiffElement>();
  private boolean showEqual = false;
  private boolean showDifferent = true;
  private boolean showNewOnSource = true;
  private boolean showNewOnTarget = true;

  public DirDiffTableModel(Project project, DiffElement src, DiffElement trg, ProgressIndicator indicator) {
    myProject = project;
    loadModel(src, trg, indicator);
  }

  public void loadModel(DiffElement src, DiffElement trg, ProgressIndicator indicator) {
    mySrc = src;
    myTrg = trg;
    final HashSet<String> files = new HashSet<String>();
    scan("", src, files, indicator, true);
    scan("", trg, files, indicator, true);
    final ArrayList<String> pathes = new ArrayList<String>(files);
    Collections.sort(pathes, new Comparator<String>() {
      @Override
      public int compare(String o1, String o2) {
        final boolean b1 = o1.endsWith("/");
        final boolean b2 = o2.endsWith("/");
        final String[] dirs1 = o1.split("/");
        final String[] dirs2 = o2.split("/");
        final int len1 = dirs1.length;
        final int len2 = dirs2.length;

        if ((!b1 && len1 == 1) || (!b2 && len2 == 1)) {
          if ((!b1 && len1 == 1) && (!b2 && len2 == 1)) {
            return dirs1[0].toLowerCase().compareTo(dirs2[0].toLowerCase());
          } else {
            return len1 == 1 ? -1 : 1;
          }
        }
        for (int i = 0; i < Math.min(len1, len2); i++) {
          final int cmp = dirs1[i].toLowerCase().compareTo(dirs2[i].toLowerCase());
          if (cmp != 0) return cmp;
        }

        return len1 - len2;
      }
    });

    for (String path : pathes) {
      final DiffElement srcFile = src.findFileByRelativePath(path);
      final DiffElement trgFile = trg.findFileByRelativePath(path);
      if (srcFile == null && trgFile != null) {
        myElements.add(DirDiffElement.createTargetOnly(trgFile));
      } else if (srcFile != null && trgFile == null) {
        myElements.add(DirDiffElement.createSourceOnly(srcFile));
      } else if (srcFile != null && trgFile != null) {
        indicator.setText2("Comparing " + path);
        if (srcFile.isContainer() && trgFile.isContainer()) {
          myElements.add(DirDiffElement.createDirElement(srcFile, trgFile, path));
        } else if (srcFile.isContainer() && !trgFile.isContainer()) {
          myElements.add(DirDiffElement.createDirElement(srcFile, null, path));
          myElements.add(DirDiffElement.createTargetOnly(trgFile));
        } else if (!srcFile.isContainer() && trgFile.isContainer()) {
          myElements.add(DirDiffElement.createDirElement(null, trgFile, path));
          myElements.add(DirDiffElement.createSourceOnly(srcFile));
        } else if (!isEqual(srcFile, trgFile)) {
            myElements.add(DirDiffElement.createChange(srcFile, trgFile));
        }
      }
    }
    removeEmptyDirs(myElements);
  }

  public String getTitle() {
    return "Diff for " + mySrc.getPath() + " and " + myTrg.getPath();
  }

  private static void removeEmptyDirs(List<DirDiffElement> elements) {
    final DirDiffElement[] tmp = elements.toArray(new DirDiffElement[elements.size()]);
    boolean prevItemIsSeparator = true;
    for (int i = tmp.length - 1; i >= 0; i--) {
      final boolean isSeparator = tmp[i].isSeparator();
      if (isSeparator) {
        if (prevItemIsSeparator) {
          elements.remove(i);
        }
        prevItemIsSeparator = true;
      } else {
        prevItemIsSeparator = false;
      }
    }
  }

  private static boolean isEqual(DiffElement file1, DiffElement file2) {
    if (file1.isContainer() || file2.isContainer()) return false;
    if (file1.getSize() != file2.getSize()) return false;
    try {
      return Arrays.equals(file1.getContent(), file2.getContent());
    }
    catch (IOException e) {
      return false;
    }
  }

  public DirDiffElement getElementAt(int index) {
    return myElements.get(index);
  }

  public DiffElement getSourceDir() {
    return mySrc;
  }

  public DiffElement getTargetDir() {
    return myTrg;
  }

  private static void scan(String prefix, DiffElement file, HashSet<String> files, ProgressIndicator indicator, boolean isRoot) {
    if (file.isContainer()) {
      indicator.setText2(file.getPath());
      String p = isRoot ? "" : prefix + file.getName() + "/";
      if (!isRoot) {
        files.add(p);
      }
      for (DiffElement f : file.getChildren()) {
        scan(p, f, files, indicator, false);
      }
    } else {
      files.add(prefix + file.getName());
    }
  }

  @Override
  public int getRowCount() {
    return myElements.size();
  }

  @Override
  public int getColumnCount() {
    return 7;
  }

  @Nullable
  @Override
  public Object getValueAt(int rowIndex, int columnIndex) {
    final DirDiffElement element = myElements.get(rowIndex);
    if (element.isSeparator()) {
      return columnIndex == 0 ? element.getName() : null;
    }
    switch (columnIndex) {
      case 0: return element.getSourceName();
      case 1: return element.getSourceSize();
      case 2: return element.getSourceModificationDate();
      case 3: return "";
      case 4: return element.getTargetModificationDate();
      case 5: return element.getTargetSize();
      case 6: return element.getTargetName();
    }
    return null;
  }

  @Override
  public String getColumnName(int column) {
    switch (column) {
      case 0: case 6: return "Name";
      case 1: case 5: return "Size";
      case 2: case 4: return "Date";
      case 3: return "*";
    }
    return "";
  }

  public Project getProject() {
    return myProject;
  }

  public boolean isShowEqual() {
    return showEqual;
  }

  public void setShowEqual(boolean show) {
    this.showEqual = show;
  }

  public boolean isShowDifferent() {
    return showDifferent;
  }

  public void setShowDifferent(boolean show) {
    this.showDifferent = show;
  }

  public boolean isShowNewOnSource() {
    return showNewOnSource;
  }

  public void setShowNewOnSource(boolean show) {
    this.showNewOnSource = show;
  }

  public boolean isShowNewOnTarget() {
    return showNewOnTarget;
  }

  public void setShowNewOnTarget(boolean show) {
    this.showNewOnTarget = show;
  }
}
