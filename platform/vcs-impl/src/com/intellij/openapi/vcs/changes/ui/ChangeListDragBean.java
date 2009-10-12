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

import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vfs.VirtualFile;

import javax.swing.*;
import java.util.List;

/**
 * @author yole
*/
class ChangeListDragBean {
  private final JComponent mySourceComponent;
  private final Change[] myChanges;
  private final List<VirtualFile> myUnversionedFiles;
  private final List<VirtualFile> myIgnoredFiles;
  private ChangesBrowserNode myTargetNode;

  public ChangeListDragBean(final JComponent sourceComponent, final Change[] changes, final List<VirtualFile> unversionedFiles,
                            final List<VirtualFile> ignoredFiles) {
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

  public List<VirtualFile> getUnversionedFiles() {
    return myUnversionedFiles;
  }

  public List<VirtualFile> getIgnoredFiles() {
    return myIgnoredFiles;
  }

  public ChangesBrowserNode getTargetNode() {
    return myTargetNode;
  }

  public void setTargetNode(final ChangesBrowserNode targetNode) {
    myTargetNode = targetNode;
  }
}
