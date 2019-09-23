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

package com.intellij.openapi.vcs.changes.ui;

import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.changes.Change;

import javax.swing.*;
import java.util.List;

/**
 * @author yole
*/
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
