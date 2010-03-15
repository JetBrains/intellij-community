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
package com.intellij.openapi.vcs.changes.patch;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diff.impl.patch.ApplyPatchStatus;
import com.intellij.openapi.diff.impl.patch.FilePatch;
import com.intellij.openapi.diff.impl.patch.TextFilePatch;
import com.intellij.openapi.diff.impl.patch.formove.PathMerger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FilePathImpl;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.changes.CurrentContentRevision;
import com.intellij.openapi.vcs.changes.SimpleContentRevision;
import com.intellij.openapi.vcs.changes.actions.ChangeDiffRequestPresentable;
import com.intellij.openapi.vcs.changes.actions.DiffRequestPresentable;
import com.intellij.openapi.vcs.changes.actions.DiffRequestPresentableProxy;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public class FilePatchInProgress implements Strippable {
  private final TextFilePatch myPatch;
  private final PatchStrippable myStrippable;
  private final FilePatchStatus myStatus;

  private VirtualFile myBase;
  private File myIoCurrentBase;
  private VirtualFile myCurrentBase;
  private boolean myBaseExists;
  private ContentRevision myNewContentRevision;
  private ContentRevision myCurrentRevision;
  private final List<VirtualFile> myAutoBases;
  private volatile Boolean myConflicts;

  private File myAfterFile;

  public FilePatchInProgress(final TextFilePatch patch, final Collection<VirtualFile> autoBases, final VirtualFile baseDir) {
    myPatch = patch.pathsOnlyCopy();
    myStrippable = new PatchStrippable(patch);
    myAutoBases = new ArrayList<VirtualFile>();
    if (autoBases != null) {
      setAutoBases(autoBases);
    }
    myStatus = getStatus(myPatch);
    if (myAutoBases.isEmpty()) {
      setNewBase(baseDir);
    } else {
      setNewBase(myAutoBases.get(0));
    }
  }

  public void setAutoBases(@NotNull final Collection<VirtualFile> autoBases) {
    final String path = myPatch.getBeforeName() == null ? myPatch.getAfterName() : myPatch.getBeforeName();
    for (VirtualFile autoBase : autoBases) {
      final VirtualFile willBeBase = PathMerger.getBase(autoBase, path);
      if (willBeBase != null) {
        myAutoBases.add(willBeBase);
      }
    }
  }

  private static FilePatchStatus getStatus(final TextFilePatch patch) {
    final String beforeName = patch.getBeforeName().replace("\\", "/");
    final String afterName = patch.getAfterName().replace("\\", "/");
    
    if (patch.isNewFile() || (beforeName == null)) {
      return FilePatchStatus.ADDED;
    } else if (patch.isDeletedFile() || (afterName == null)) {
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
    myAfterFile = null;
    myConflicts = null;

    final String beforeName = myPatch.getBeforeName();
    if (beforeName != null) {
      myIoCurrentBase = PathMerger.getFile(new File(myBase.getPath()), beforeName);
      myCurrentBase = VcsUtil.getVirtualFileWithRefresh(myIoCurrentBase);
      myBaseExists = (myCurrentBase != null) && myCurrentBase.exists();
    } else {
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

  public TextFilePatch getPatch() {
    return myPatch;
  }

  public boolean isBaseExists() {
    return myBaseExists;
  }

  public boolean baseExistsOrAdded() {
    return myBaseExists || FilePatchStatus.ADDED.equals(myStatus);
  }

  public ContentRevision getNewContentRevision() {
    if (FilePatchStatus.DELETED.equals(myStatus)) return null;

    if (myNewContentRevision == null) {
      myConflicts = null;
      if (FilePatchStatus.ADDED.equals(myStatus)) {
        final FilePath newFilePath = FilePathImpl.createNonLocal(myIoCurrentBase.getAbsolutePath(), false);
        final String content = myPatch.getNewFileText();
        myNewContentRevision = new SimpleContentRevision(content, newFilePath, myPatch.getAfterVersionId());
      } else {
        final FilePath newFilePath;
        if (FilePatchStatus.MOVED_OR_RENAMED.equals(myStatus)) {
          newFilePath = new FilePathImpl(PathMerger.getFile(new File(myBase.getPath()), myPatch.getAfterName()), false);
        } else {
          newFilePath = (myCurrentBase != null) ? new FilePathImpl(myCurrentBase) : new FilePathImpl(myIoCurrentBase, false);
        }
        myNewContentRevision = new LazyPatchContentRevision(myCurrentBase, newFilePath, myPatch.getAfterVersionId(), myPatch);
        if (myCurrentBase != null) {
          ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
            public void run() {
              ((LazyPatchContentRevision) myNewContentRevision).getContent();
            }
          });
        }
      }
    }
    return myNewContentRevision;
  }

  public boolean isConflictingChange() {
    if (myConflicts == null) {
      if ((myCurrentBase != null) && (myNewContentRevision instanceof LazyPatchContentRevision)) {
        ((LazyPatchContentRevision) myNewContentRevision).getContent();
        myConflicts = ((LazyPatchContentRevision) myNewContentRevision).isPatchApplyFailed();
      } else {
        myConflicts = false;
      }
    }
    return myConflicts;
  }

  public ContentRevision getCurrentRevision() {
    if (FilePatchStatus.ADDED.equals(myStatus)) return null; 
    if (myCurrentRevision == null) {
      final FilePathImpl filePath = (myCurrentBase != null) ? new FilePathImpl(myCurrentBase) : new FilePathImpl(myIoCurrentBase, false);
      myCurrentRevision = new CurrentContentRevision(filePath);
    }
    return myCurrentRevision;
  }

  public static class PatchChange extends Change {
    private final FilePatchInProgress myPatchInProgress;

    public PatchChange(ContentRevision beforeRevision, ContentRevision afterRevision, FilePatchInProgress patchInProgress) {
      super(beforeRevision, afterRevision,
            patchInProgress.isBaseExists() || FilePatchStatus.ADDED.equals(patchInProgress.getStatus()) ? null : FileStatus.MERGED_WITH_CONFLICTS);
      myPatchInProgress = patchInProgress;
    }

    public FilePatchInProgress getPatchInProgress() {
      return myPatchInProgress;
    }

    @Nullable
    public DiffRequestPresentable createDiffRequestPresentable(final Project project) {
      return new DiffRequestPresentableProxy() {
        @Override
        protected DiffRequestPresentable init() {
          if (myPatchInProgress.isConflictingChange()) {
            final ApplyPatchForBaseRevisionTexts texts = ApplyPatchForBaseRevisionTexts
              .create(project, myPatchInProgress.getCurrentBase(), new FilePathImpl(myPatchInProgress.getCurrentBase()),
                      myPatchInProgress.getPatch());
            if ((texts != null) &&
                (ApplyPatchStatus.SUCCESS.equals(texts.getStatus()) || ApplyPatchStatus.ALREADY_APPLIED.equals(texts.getStatus()))) {
              return new MergedDiffRequestPresentable(project, texts,
                      myPatchInProgress.getCurrentBase(), myPatchInProgress.getPatch().getAfterVersionId());
            }
            return null;
          } else {
            return new ChangeDiffRequestPresentable(project, PatchChange.this);
          }
        }
      };
    }
  }


  public List<VirtualFile> getAutoBasesCopy() {
    final ArrayList<VirtualFile> result = new ArrayList<VirtualFile>(myAutoBases.size() + 1);
    result.addAll(myAutoBases);
    return result;
  }

  public Pair<String, String> getKey() {
    return new Pair<String, String>(myPatch.getBeforeName(), myPatch.getAfterName());
  }

  private void refresh() {
    myStrippable.applyBackToPatch(myPatch);
    setNewBase(myBase);
  }

  public void reset() {
    myStrippable.reset();
    refresh();
  }

  public boolean canDown() {
    return myStrippable.canDown();
  }

  public boolean canUp() {
    return myStrippable.canUp();
  }

  public void up() {
    myStrippable.up();
    refresh();
  }

  public void down() {
    myStrippable.down();
    refresh();
  }

  public void setZero() {
    myStrippable.setZero();
    refresh();
  }

  public String getCurrentPath() {
    return myStrippable.getCurrentPath();
  }

  public int getCurrentStrip() {
    return myStrippable.getCurrentStrip();
  }

  private static class StripCapablePath implements Strippable {
    private final int myStripMax;
    private int myCurrentStrip;
    private final StringBuilder mySourcePath;
    private final int[] myParts;

    private StripCapablePath(final String path) {
      final String corrected = path.trim().replace('\\', '/');
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

    public void reset() {
      myCurrentStrip = 0;
    }

    public int getCurrentStrip() {
      return myCurrentStrip;
    }

    // down - restore dirs...
    public boolean canDown() {
      return myCurrentStrip > 0;
    }

    public boolean canUp() {
      return myCurrentStrip < myStripMax;
    }

    public void up() {
      if (canUp()) {
        ++ myCurrentStrip;
      }
    }

    public void down() {
      if (canDown()) {
        -- myCurrentStrip;
      }
    }

    public void setZero() {
      myCurrentStrip = myStripMax;
    }

    public String getCurrentPath() {
      return mySourcePath.substring(myParts[myCurrentStrip]);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      StripCapablePath that = (StripCapablePath)o;

      if (!mySourcePath.equals(that.mySourcePath)) return false;

      return true;
    }

    @Override
    public int hashCode() {
      return mySourcePath.hashCode();
    }
  }

  private static class PatchStrippable implements Strippable {
    private final Strippable[] myParts;
    private final int myBeforeIdx;
    private final int myAfterIdx;

    private PatchStrippable(final FilePatch patch) {
      final boolean onePath = patch.isDeletedFile() || patch.isNewFile() || Comparing.equal(patch.getAfterName(), patch.getBeforeName());
      final int size = onePath ? 1 : 2;
      myParts = new Strippable[size];

      int cnt = 0;
      if (patch.getAfterName() != null) {
        myAfterIdx = 0;
        myParts[cnt] = new StripCapablePath(patch.getAfterName());
        ++ cnt;
      } else {
        myAfterIdx = -1;
      }
      if (cnt < size) {
        myParts[cnt] = new StripCapablePath(patch.getBeforeName());
        myBeforeIdx = cnt;
      } else {
        myBeforeIdx = 0;
      }
    }

    public void reset() {
      for (Strippable part : myParts) {
        part.reset();
      }
    }

    public boolean canDown() {
      boolean result = true;
      for (Strippable part : myParts) {
        result &= part.canDown();
      }
      return result;
    }

    public boolean canUp() {
      boolean result = true;
      for (Strippable part : myParts) {
        result &= part.canUp();
      }
      return result;
    }

    public void up() {
      for (Strippable part : myParts) {
        part.up();
      }
    }

    public void down() {
      for (Strippable part : myParts) {
        part.down();
      }
    }

    public void setZero() {
      for (Strippable part : myParts) {
        part.setZero();
      }
    }

    public String getCurrentPath() {
      return myParts[0].getCurrentPath();
    }

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

    FilePatchInProgress that = (FilePatchInProgress)o;

    if (!myStrippable.equals(that.myStrippable)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    return myStrippable.hashCode();
  }
}
