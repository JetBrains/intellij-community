// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs.newvfs.persistent;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.io.FileAttributes;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.NewVirtualFile;
import com.intellij.openapi.vfs.newvfs.events.*;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

class VfsEventGenerationHelper {
  static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vfs.newvfs.persistent.RefreshWorker");

  private final List<VFileEvent> myEvents = new ArrayList<>();

  @NotNull
  public List<VFileEvent> getEvents() {
    return myEvents;
  }

  boolean checkDirty(@NotNull NewVirtualFile file) {
    boolean fileDirty = file.isDirty();
    if (LOG.isTraceEnabled()) LOG.trace("file=" + file + " dirty=" + fileDirty);
    return fileDirty;
  }

  void checkContentChanged(@NotNull VirtualFile file, long oldTimestamp, long newTimestamp, long oldLength, long newLength) {
    if (oldTimestamp != newTimestamp || oldLength != newLength) {
      if (LOG.isTraceEnabled()) LOG.trace(
        "update file=" + file +
        (oldTimestamp != newTimestamp ? " TS=" + oldTimestamp + "->" + newTimestamp : "") +
        (oldLength != newLength ? " len=" + oldLength + "->" + newLength : ""));
      myEvents.add(new VFileContentChangeEvent(null, file, file.getModificationStamp(), -1, oldTimestamp, newTimestamp, oldLength, newLength, true));
    }
  }

  void scheduleCreation(@NotNull VirtualFile parent,
                        @NotNull String childName,
                        @NotNull FileAttributes attributes,
                        boolean isEmptyDir,
                        String symlinkTarget) {
    if (LOG.isTraceEnabled()) LOG.trace("create parent=" + parent + " name=" + childName + " attr=" + attributes);
    myEvents.add(new VFileCreateEvent(null, parent, childName, attributes.isDirectory(), attributes, symlinkTarget, true, isEmptyDir));
  }

  void scheduleDeletion(@NotNull VirtualFile file) {
    if (LOG.isTraceEnabled()) LOG.trace("delete file=" + file);
    myEvents.add(new VFileDeleteEvent(null, file, true));
  }

  void checkSymbolicLinkChange(@NotNull VirtualFile child, String oldTarget, String currentTarget) {
    String currentVfsTarget = currentTarget != null ? FileUtil.toSystemIndependentName(currentTarget) : null;
    if (!Comparing.equal(oldTarget, currentVfsTarget)) {
      scheduleAttributeChange(child, VirtualFile.PROP_SYMLINK_TARGET, oldTarget, currentVfsTarget);
    }
  }

  void checkHiddenAttributeChange(@NotNull VirtualFile child, boolean oldHidden, boolean newHidden) {
    if (oldHidden != newHidden) {
      scheduleAttributeChange(child, VirtualFile.PROP_HIDDEN, oldHidden, newHidden);
    }
  }

  void checkWritableAttributeChange(@NotNull VirtualFile file, boolean oldWritable, boolean newWritable) {
    if (oldWritable != newWritable) {
      scheduleAttributeChange(file, VirtualFile.PROP_WRITABLE, oldWritable, newWritable);
    }
  }

  void scheduleAttributeChange(@NotNull VirtualFile file, @VirtualFile.PropName @NotNull String property, Object current, Object upToDate) {
    if (LOG.isTraceEnabled()) LOG.trace("update file=" + file + ' ' + property + '=' + current + "->" + upToDate);
    myEvents.add(new VFilePropertyChangeEvent(null, file, property, current, upToDate, true));
  }

  void addAllEventsFrom(@NotNull VfsEventGenerationHelper otherHelper) {
    myEvents.addAll(otherHelper.myEvents);
  }
}