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
   * An editor, an editor tab, or the file editor splitter accepts a drop only if the transferable
   * offers a file list flavor.
   * <p>
   * Note that the platform offers the file list flavor for every {@link FileFlavorProvider}, before it calls
   * this method. A target can therefore show a drop cursor even when the result is {@code null}.
   * <p>
   * The method runs on the drag thread, so it must not do blocking IO.
   *
   * @return the local files, or {@code null} if the drag has none. Do not return an empty list. The platform
   * then reports a drop of zero files, and a handler such as the Markdown one runs an empty write command.
   * {@code null} makes the platform stop instead.
   */
  @Override
  @SuppressWarnings({"IO_FILE_USAGE", "UnnecessaryFullyQualifiedName"})
  public @Nullable List<java.io.File> asFileList() {
    List<java.io.File> files = new ArrayList<>();
    for (Change change : myChanges) {
      // The after path is the current location on disk. It is null for a deletion, which has no file to drag.
      addLocalFile(files, ChangesUtil.getAfterPath(change));
    }
    for (FilePath path : myUnversionedFiles) {
      addLocalFile(files, path);
    }
    for (FilePath path : myIgnoredFiles) {
      addLocalFile(files, path);
    }
    return files.isEmpty() ? null : files;
  }

  @SuppressWarnings({"IO_FILE_USAGE", "UnnecessaryFullyQualifiedName"})
  private static void addLocalFile(@NotNull List<java.io.File> files, @Nullable FilePath path) {
    // A directory is not a meaningful drop on an editor, so it stays out of the list.
    if (path == null || path.isNonLocal() || path.isDirectory()) return;
    files.add(path.getIOFile());
  }
}
