// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.openapi.vcs.changes.ui;

import com.intellij.ide.dnd.FileFlavorProvider;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.platform.vcs.changes.ChangesUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComponent;
import java.util.ArrayList;
import java.util.List;

@ApiStatus.Internal
public class ChangeListDragBean implements FileFlavorProvider {
  private final JComponent mySourceComponent;
  private final List<Change> myChanges;
  private final List<FilePath> myUnversionedFiles;
  private final List<FilePath> myIgnoredFiles;
  private ChangesBrowserNode myTargetNode;

  public ChangeListDragBean(@NotNull JComponent sourceComponent,
                            @NotNull List<Change> changes,
                            @NotNull List<FilePath> unversionedFiles,
                            @NotNull List<FilePath> ignoredFiles) {
    mySourceComponent = sourceComponent;
    myChanges = changes;
    myUnversionedFiles = unversionedFiles;
    myIgnoredFiles = ignoredFiles;
  }

  public JComponent getSourceComponent() {
    return mySourceComponent;
  }

  public List<Change> getChanges() {
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

  /**
   * Exposes the dragged rows as local files. This lets a target outside the changes tree accept the drag.
   * See {@link ChangesTreeFileDragBean#localFileList} for the mapping rule and the reason for a {@code null} result.
   */
  @Override
  @SuppressWarnings({"IO_FILE_USAGE", "UnnecessaryFullyQualifiedName"})
  public @Nullable List<java.io.File> asFileList() {
    List<FilePath> paths = new ArrayList<>();
    for (Change change : myChanges) {
      // The after path is the location of the change on disk. It is null for a deletion, which has no file to drag.
      paths.add(ChangesUtil.getAfterPath(change));
    }
    paths.addAll(myUnversionedFiles);
    paths.addAll(myIgnoredFiles);
    return ChangesTreeFileDragBean.localFileList(paths);
  }
}
