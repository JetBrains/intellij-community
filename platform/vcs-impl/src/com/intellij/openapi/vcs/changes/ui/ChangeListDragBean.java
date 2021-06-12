// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.vcs.changes.ui;

import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.changes.Change;

import javax.swing.*;
import java.util.List;

public class ChangeListDragBean {
  private final JComponent mySourceComponent;
  private final Change[] myChanges;
  private final List<FilePath> myUnversionedFiles;
  private final List<FilePath> myIgnoredFiles;
  private ChangesBrowserNode myTargetNode;

  public ChangeListDragBean(final JComponent sourceComponent, final Change[] changes, final List<FilePath> unversionedFiles,
                            final List<FilePath> ignoredFiles) {
    mySourceComponent = sourceComponent;
    myChanges = changes;
    myUnversionedFiles = unversionedFiles;
    myIgnoredFiles = ignoredFiles;
  }

  public JComponent getSourceComponent() {
    return mySourceComponent;
  }

  public Change[] getChanges() {
    return myChanges;
  }

  public List<FilePath> getUnversionedFiles() {
    return myUnversionedFiles;
  }

  public List<FilePath> getIgnoredFiles() {
    return myIgnoredFiles;
  }

  public ChangesBrowserNode getTargetNode() {
    return myTargetNode;
  }

  public void setTargetNode(final ChangesBrowserNode targetNode) {
    myTargetNode = targetNode;
  }
}
