// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.impl.VcsPathPresenter;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.*;

import javax.swing.*;
import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class Change {
  public static final Change[] EMPTY_CHANGE_ARRAY = new Change[0];

  private int myHash = -1;

  public enum Type {
    MODIFICATION,
    NEW,
    DELETED,
    MOVED
  }

  private final ContentRevision myBeforeRevision;
  private final ContentRevision myAfterRevision;
  private final FileStatus myFileStatus;
  protected String myMoveRelativePath;
  protected boolean myRenamed;
  protected boolean myMoved;
  protected boolean myRenameOrMoveCached = false;
  private boolean myIsReplaced;
  private Type myType;
  private Map<String, Change> myOtherLayers;

  public Change(@Nullable ContentRevision beforeRevision, @Nullable ContentRevision afterRevision) {
    this(beforeRevision, afterRevision, convertStatus(beforeRevision, afterRevision));
  }

  public Change(@Nullable ContentRevision beforeRevision,
                @Nullable ContentRevision afterRevision,
                @Nullable FileStatus fileStatus) {
    assert beforeRevision != null || afterRevision != null;
    myBeforeRevision = beforeRevision;
    myAfterRevision = afterRevision;
    myFileStatus = fileStatus == null ? convertStatus(beforeRevision, afterRevision) : fileStatus;
  }
  
  @ApiStatus.Internal
  public Change(@Nullable ContentRevision beforeRevision,
                @Nullable ContentRevision afterRevision,
                @Nullable FileStatus fileStatus,
                @NotNull Change change) {
    this(beforeRevision, afterRevision, fileStatus);
    copyFieldsFrom(change);
  }

  protected void copyFieldsFrom(@NotNull Change change) {
    myOtherLayers = change.myOtherLayers != null ? new HashMap<>(change.myOtherLayers) : null;
    myIsReplaced = change.isIsReplaced();
  }

  private static FileStatus convertStatus(@Nullable ContentRevision beforeRevision, @Nullable ContentRevision afterRevision) {
    if (beforeRevision == null) return FileStatus.ADDED;
    if (afterRevision == null) return FileStatus.DELETED;
    return FileStatus.MODIFIED;
  }

  /**
   * For SVN: used to show 'file property' changes.
   *
   * @see com.intellij.openapi.vcs.changes.actions.diff.ChangeDiffViewerWrapperProvider
   */
  public void addAdditionalLayerElement(@NonNls String name, final Change change) {
    if (myOtherLayers == null) myOtherLayers = new HashMap<>(1);
    myOtherLayers.put(name, change);
  }

  public @NotNull Map<@NonNls String, Change> getOtherLayers() {
    return ContainerUtil.notNullize(myOtherLayers);
  }

  public @NotNull Type getType() {
    Type type = myType;
    if (type == null) {
      myType = type = calcType();
    }
    return type;
  }

  private @NotNull Type calcType() {
    if (myBeforeRevision == null) return Type.NEW;
    if (myAfterRevision == null) return Type.DELETED;

    FilePath bFile = myBeforeRevision.getFile();
    FilePath aFile = myAfterRevision.getFile();
    if (!Comparing.equal(bFile, aFile)) return Type.MOVED;

    // enforce case-sensitive check
    if (!SystemInfo.isFileSystemCaseSensitive) {
      String bPath = bFile.getPath();
      String aPath = aFile.getPath();
      if (!bPath.equals(aPath) && bPath.equalsIgnoreCase(aPath)) return Type.MOVED;
    }

    return Type.MODIFICATION;
  }

  public @Nullable ContentRevision getBeforeRevision() {
    return myBeforeRevision;
  }

  public @Nullable ContentRevision getAfterRevision() {
    return myAfterRevision;
  }

  public @NotNull FileStatus getFileStatus() {
    return myFileStatus;
  }

  public @Nullable VirtualFile getVirtualFile() {
    return myAfterRevision == null ? null : myAfterRevision.getFile().getVirtualFile();
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if ((!(o instanceof Change otherChange))) return false;

    final ContentRevision br1 = getBeforeRevision();
    final ContentRevision br2 = otherChange.getBeforeRevision();
    final ContentRevision ar1 = getAfterRevision();
    final ContentRevision ar2 = otherChange.getAfterRevision();

    FilePath fbr1 = br1 != null ? br1.getFile() : null;
    FilePath fbr2 = br2 != null ? br2.getFile() : null;

    FilePath far1 = ar1 != null ? ar1.getFile() : null;
    FilePath far2 = ar2 != null ? ar2.getFile() : null;

    return Comparing.equal(fbr1, fbr2) && Comparing.equal(far1, far2);
  }

  @Override
  public int hashCode() {
    if (myHash == -1) {
      myHash = calculateHash();
    }
    return myHash;
  }

  private int calculateHash() {
    return revisionHashCode(getBeforeRevision()) * 27 + revisionHashCode(getAfterRevision());
  }

  private static int revisionHashCode(@Nullable ContentRevision rev) {
    return rev != null ? rev.getFile().hashCode() : 0;
  }

  public boolean affectsFile(File ioFile) {
    if (myBeforeRevision != null && myBeforeRevision.getFile().getIOFile().equals(ioFile)) return true;
    if (myAfterRevision != null && myAfterRevision.getFile().getIOFile().equals(ioFile)) return true;
    return false;
  }

  public boolean isRenamed() {
    cacheRenameOrMove();
    return myRenamed;
  }

  public boolean isMoved() {
    cacheRenameOrMove();
    return myMoved;
  }

  public String getMoveRelativePath(Project project) {
    cacheMoveRelativePath(project);
    return myMoveRelativePath;
  }

  private void cacheRenameOrMove() {
    if (myBeforeRevision == null || myAfterRevision == null) return;
    if (myRenameOrMoveCached) return;
    myRenameOrMoveCached = true;

    FilePath beforePath = myBeforeRevision.getFile();
    FilePath afterPath = myAfterRevision.getFile();
    // intentionally comparing case-sensitively even on case-insensitive OS to identify case-only renames
    if (beforePath.getPath().equals(afterPath.getPath())) return;

    if (Comparing.equal(beforePath.getParentPath(), afterPath.getParentPath())) {
      myRenamed = true;
    }
    else {
      myMoved = true;
    }
  }

  private void cacheMoveRelativePath(final Project project) {
    cacheRenameOrMove();
    if (!myMoved) return;

    if (myBeforeRevision == null || myAfterRevision == null) return;
    if (myMoveRelativePath != null) return;

    if (project != null && !project.isDisposed()) {
      // cache value for the first Project passed (we do not expect Change to be reused with multiple projects)
      myMoveRelativePath = VcsPathPresenter.getInstance(project).getPresentableRelativePath(myBeforeRevision, myAfterRevision);
    }
  }

  @Override
  public @NonNls String toString() {
    final Type type = getType();
    return switch (type) {
      case NEW -> "A: " + myAfterRevision;
      case DELETED -> "D: " + myBeforeRevision;
      case MOVED -> "M: " + myBeforeRevision + " -> " + myAfterRevision;
      default -> "M: " + myAfterRevision;
    };
  }

  public @Nullable @Nls String getOriginText(final Project project) {
    cacheMoveRelativePath(project);
    if (isMoved()) {
      return getMovedText(project);
    }
    else if (isRenamed()) {
      return getRenamedText();
    }
    return myIsReplaced ? VcsBundle.message("change.file.replaced.text") : null;
  }

  protected @Nullable @Nls String getRenamedText() {
    return VcsBundle.message("change.file.renamed.from.text", myBeforeRevision.getFile().getName());
  }

  protected @Nullable @Nls String getMovedText(final Project project) {
    return VcsBundle.message("change.file.moved.from.text", getMoveRelativePath(project));
  }

  /**
   * For SVN: the file was scheduled for deletion, and then a new file was scheduled for addition on its place.
   */
  public boolean isIsReplaced() {
    return myIsReplaced;
  }

  public void setIsReplaced(final boolean isReplaced) {
    myIsReplaced = isReplaced;
  }

  public @Nullable Icon getAdditionalIcon() {
    return null;
  }

  public @Nls @Nullable String getDescription() {
    return null;
  }
}
