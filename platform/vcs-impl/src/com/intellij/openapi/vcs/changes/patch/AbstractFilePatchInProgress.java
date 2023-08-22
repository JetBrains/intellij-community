// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.patch;

import com.intellij.diff.chains.DiffRequestProducer;
import com.intellij.openapi.diff.impl.patch.FilePatch;
import com.intellij.openapi.diff.impl.patch.PatchReader;
import com.intellij.openapi.diff.impl.patch.formove.PathMerger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.changes.CurrentContentRevision;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.PathUtil;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.*;

public abstract class AbstractFilePatchInProgress<T extends FilePatch> implements Strippable {
  protected final T myPatch;
  private final PatchStrippable myStrippable;
  protected final FilePatchStatus myStatus;

  private VirtualFile myBase;
  protected File myIoCurrentBase;
  protected VirtualFile myCurrentBase;
  private boolean myBaseExists;
  protected ContentRevision myNewContentRevision;
  private ContentRevision myCurrentRevision;
  private final List<VirtualFile> myAutoBases;
  protected volatile Boolean myConflicts;

  protected AbstractFilePatchInProgress(T patch, Collection<VirtualFile> autoBases, VirtualFile baseDir) {
    myPatch = patch; //should be a copy of FilePatch! because names may be changes during processing variants
    myStrippable = new PatchStrippable(patch);
    myAutoBases = new ArrayList<>();
    if (autoBases != null) {
      setAutoBases(autoBases);
    }
    myStatus = getStatus(myPatch);
    if (myAutoBases.isEmpty()) {
      setNewBase(baseDir);
    }
    else {
      setNewBase(myAutoBases.get(0));
    }
  }

  public void setAutoBases(@NotNull final Collection<? extends VirtualFile> autoBases) {
    final String path = myPatch.getBeforeName() == null ? myPatch.getAfterName() : myPatch.getBeforeName();
    for (VirtualFile autoBase : autoBases) {
      final VirtualFile willBeBase = PathMerger.getBase(autoBase, path);
      if (willBeBase != null) {
        myAutoBases.add(willBeBase);
      }
    }
  }

  private FilePatchStatus getStatus(final T patch) {
    final String beforeName = PathUtil.toSystemIndependentName(patch.getBeforeName());
    final String afterName = PathUtil.toSystemIndependentName(patch.getAfterName());

    if (patch.isNewFile() || (beforeName == null)) {
      return FilePatchStatus.ADDED;
    }
    else if (patch.isDeletedFile() || (afterName == null)) {
      return FilePatchStatus.DELETED;
    }

    if (beforeName.equals(afterName)) return FilePatchStatus.MODIFIED;
    return FilePatchStatus.MOVED_OR_RENAMED;
  }

  public PatchChange getChange() {
    return new PatchChange(getCurrentRevision(), getNewContentRevision(), this);
  }

  public void setNewBase(final VirtualFile base) {
    myBase = base;
    myNewContentRevision = null;
    myCurrentRevision = null;
    myConflicts = null;

    final String beforeName = myPatch.getBeforeName();
    if (beforeName != null) {
      myIoCurrentBase = PathMerger.getFile(new File(myBase.getPath()), beforeName);
      myCurrentBase = myIoCurrentBase == null ? null : VcsUtil.getVirtualFileWithRefresh(myIoCurrentBase);
      myBaseExists = (myCurrentBase != null) && myCurrentBase.exists();
    }
    else {
      // creation
      final String afterName = myPatch.getAfterName();
      myBaseExists = true;
      myIoCurrentBase = PathMerger.getFile(new File(myBase.getPath()), afterName);
      myCurrentBase = VcsUtil.getVirtualFileWithRefresh(myIoCurrentBase);
    }
  }

  public void setCreatedCurrentBase(final VirtualFile vf) {
    myCurrentBase = vf;
  }

  public FilePatchStatus getStatus() {
    return myStatus;
  }

  public File getIoCurrentBase() {
    return myIoCurrentBase;
  }

  public VirtualFile getCurrentBase() {
    return myCurrentBase;
  }

  public VirtualFile getBase() {
    return myBase;
  }

  public T getPatch() {
    return myPatch;
  }

  private boolean isBaseExists() {
    return myBaseExists;
  }

  public boolean baseExistsOrAdded() {
    return myBaseExists || FilePatchStatus.ADDED.equals(myStatus);
  }

  protected abstract ContentRevision getNewContentRevision();

  @NotNull
  protected FilePath getFilePath() {
    return FilePatchStatus.ADDED.equals(myStatus) ? VcsUtil.getFilePath(myIoCurrentBase, false)
                                                  : detectNewFilePathForMovedOrModified();
  }

  @NotNull
  protected FilePath detectNewFilePathForMovedOrModified() {
    return FilePatchStatus.MOVED_OR_RENAMED.equals(myStatus)
           ? VcsUtil.getFilePath(Objects.requireNonNull(PathMerger.getFile(new File(myBase.getPath()), myPatch.getAfterName())), false)
           : (myCurrentBase != null) ? VcsUtil.getFilePath(myCurrentBase) : VcsUtil.getFilePath(myIoCurrentBase, false);
  }

  protected boolean isConflictingChange() {
    if (myConflicts == null) {
      if ((myCurrentBase != null) && (myNewContentRevision instanceof LazyPatchContentRevision)) {
        ((LazyPatchContentRevision)myNewContentRevision).getContent();
        myConflicts = ((LazyPatchContentRevision)myNewContentRevision).isPatchApplyFailed();
      }
      else {
        myConflicts = false;
      }
    }
    return myConflicts;
  }

  private ContentRevision getCurrentRevision() {
    if (FilePatchStatus.ADDED.equals(myStatus)) return null;
    if (myCurrentRevision == null) {
      FilePath filePath = (myCurrentBase != null) ? VcsUtil.getFilePath(myCurrentBase) : VcsUtil.getFilePath(myIoCurrentBase, false);
      myCurrentRevision = new CurrentContentRevision(filePath);
    }
    return myCurrentRevision;
  }

  public static final class PatchChange extends Change {
    private final AbstractFilePatchInProgress<?> myPatchInProgress;

    public PatchChange(ContentRevision beforeRevision, ContentRevision afterRevision, AbstractFilePatchInProgress<?> patchInProgress) {
      super(beforeRevision, afterRevision,
            patchInProgress.isBaseExists() || FilePatchStatus.ADDED.equals(patchInProgress.getStatus())
            ? null
            : FileStatus.MERGED_WITH_CONFLICTS);
      myPatchInProgress = patchInProgress;
    }

    public AbstractFilePatchInProgress<?> getPatchInProgress() {
      return myPatchInProgress;
    }

    public boolean isValid() {
      return myPatchInProgress.baseExistsOrAdded();
    }
  }

  @NotNull
  public abstract DiffRequestProducer getDiffRequestProducers(Project project, PatchReader baseContents);

  public List<VirtualFile> getAutoBasesCopy() {
    List<VirtualFile> result = new ArrayList<>(myAutoBases.size() + 1);
    result.addAll(myAutoBases);
    return result;
  }

  public Couple<String> getKey() {
    return Couple.of(myPatch.getBeforeName(), myPatch.getAfterName());
  }

  private void refresh() {
    myStrippable.applyBackToPatch(myPatch);
    setNewBase(myBase);
  }

  @Override
  public void reset() {
    myStrippable.reset();
    refresh();
  }

  @Override
  public boolean canDown() {
    return myStrippable.canDown();
  }

  @Override
  public boolean canUp() {
    return myStrippable.canUp();
  }

  @Override
  public void up() {
    myStrippable.up();
    refresh();
  }

  @Override
  public void down() {
    myStrippable.down();
    refresh();
  }

  @Override
  public void setZero() {
    myStrippable.setZero();
    refresh();
  }

  @Override
  public @NlsSafe String getCurrentPath() {
    return myStrippable.getCurrentPath();
  }

  @NotNull
  public String getOriginalBeforePath() {
    return myStrippable.getOriginalBeforePath();
  }

  @Override
  public int getCurrentStrip() {
    return myStrippable.getCurrentStrip();
  }

  private static final class StripCapablePath implements Strippable {
    private final int myStripMax;
    private int myCurrentStrip;

    private final StringBuilder mySourcePath;
    private final int[] myParts;

    private StripCapablePath(final String path) {
      final String corrected = PathUtil.toSystemIndependentName(path.trim());
      mySourcePath = new StringBuilder(corrected);
      final String[] steps = corrected.split("/");
      myStripMax = steps.length - 1;
      myParts = new int[steps.length];
      int pos = 0;
      for (int i = 0; i < steps.length; i++) {
        final String step = steps[i];
        myParts[i] = pos;
        pos += step.length() + 1; // plus 1 for separator
      }
      myCurrentStrip = 0;
    }

    @Override
    public void reset() {
      myCurrentStrip = 0;
    }

    @Override
    public int getCurrentStrip() {
      return myCurrentStrip;
    }

    // down - restore dirs...
    @Override
    public boolean canDown() {
      return myCurrentStrip > 0;
    }

    @Override
    public boolean canUp() {
      return myCurrentStrip < myStripMax;
    }

    @Override
    public void up() {
      if (canUp()) {
        ++myCurrentStrip;
      }
    }

    @Override
    public void down() {
      if (canDown()) {
        --myCurrentStrip;
      }
    }

    @Override
    public void setZero() {
      myCurrentStrip = myStripMax;
    }

    @Override
    public String getCurrentPath() {
      return mySourcePath.substring(myParts[myCurrentStrip]);
    }

    @NotNull
    private String getOriginalPath() {
      return mySourcePath.toString();
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      StripCapablePath that = (StripCapablePath)o;

      return StringUtil.equals(mySourcePath, that.mySourcePath);
    }

    @Override
    public int hashCode() {
      return mySourcePath.hashCode();
    }
  }

  private static final class PatchStrippable implements Strippable {
    private final StripCapablePath[] myParts;
    private final int myBeforeIdx;
    private final int myAfterIdx;

    private PatchStrippable(final FilePatch patch) {
      final boolean onePath = patch.isDeletedFile() || patch.isNewFile() || Objects.equals(patch.getAfterName(), patch.getBeforeName());
      final int size = onePath ? 1 : 2;
      myParts = new StripCapablePath[size];

      int cnt = 0;
      if (patch.getAfterName() != null) {
        myAfterIdx = 0;
        myParts[cnt] = new StripCapablePath(patch.getAfterName());
        ++cnt;
      }
      else {
        myAfterIdx = -1;
      }
      if (cnt < size) {
        myParts[cnt] = new StripCapablePath(patch.getBeforeName());
        myBeforeIdx = cnt;
      }
      else {
        myBeforeIdx = 0;
      }
    }

    @Override
    public void reset() {
      for (Strippable part : myParts) {
        part.reset();
      }
    }

    @Override
    public boolean canDown() {
      boolean result = true;
      for (Strippable part : myParts) {
        result &= part.canDown();
      }
      return result;
    }

    @Override
    public boolean canUp() {
      boolean result = true;
      for (Strippable part : myParts) {
        result &= part.canUp();
      }
      return result;
    }

    @Override
    public void up() {
      for (Strippable part : myParts) {
        part.up();
      }
    }

    @Override
    public void down() {
      for (Strippable part : myParts) {
        part.down();
      }
    }

    @Override
    public void setZero() {
      for (Strippable part : myParts) {
        part.setZero();
      }
    }

    @Override
    public String getCurrentPath() {
      return myParts[0].getCurrentPath();
    }

    @NotNull
    private String getOriginalBeforePath() {
      return myParts[myBeforeIdx].getOriginalPath();
    }

    @Override
    public int getCurrentStrip() {
      return myParts[0].getCurrentStrip();
    }

    public void applyBackToPatch(final FilePatch patch) {
      final String beforeName = patch.getBeforeName();
      if (beforeName != null) {
        patch.setBeforeName(myParts[myBeforeIdx].getCurrentPath());
      }
      final String afterName = patch.getAfterName();
      if (afterName != null) {
        patch.setAfterName(myParts[myAfterIdx].getCurrentPath());
      }
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      PatchStrippable that = (PatchStrippable)o;

      if (!Arrays.equals(myParts, that.myParts)) return false;

      return true;
    }

    @Override
    public int hashCode() {
      return Arrays.hashCode(myParts);
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    AbstractFilePatchInProgress<?> that = (AbstractFilePatchInProgress<?>)o;
    if (!myStrippable.equals(that.myStrippable)) return false;
    return true;
  }

  @Override
  public int hashCode() {
    return myStrippable.hashCode();
  }
}
