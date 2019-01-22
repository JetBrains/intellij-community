// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs.newvfs.persistent;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.events.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

class VfsEventGenerationHelper {
  protected static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vfs.newvfs.persistent.RefreshWorker");
  private static final Logger LOG_ATTRIBUTES = Logger.getInstance("#com.intellij.openapi.vfs.newvfs.persistent.RefreshWorker_Attributes");
  private final List<VFileEvent> myEvents = new ArrayList<>();
  
  @NotNull
  public List<VFileEvent> getEvents() {
    return myEvents;
  }

  void scheduleAttributeChange(@NotNull VirtualFile file, @VirtualFile.PropName @NotNull String property, Object current, Object upToDate) {
    if (LOG.isTraceEnabled()) LOG.trace("update '" + property + "' file=" + file);
    myEvents.add(new VFilePropertyChangeEvent(null, file, property, current, upToDate, true));
  }

  void checkContentChanged(@NotNull VirtualFile file, long oldTimestamp, long newTimestamp, long oldLength, long newLength) {
    if (oldTimestamp != newTimestamp || oldLength != newLength) {
      scheduleUpdateContent(file, oldTimestamp, newTimestamp, oldLength, newLength);
    }
  }
  
  void scheduleUpdateContent(@NotNull VirtualFile file, long oldTimestamp, long newTimestamp, long oldLength, long newLength) {
    if (LOG.isTraceEnabled()) {
      LOG.trace(
        "update file=" + file + 
        (oldTimestamp != newTimestamp ? ", oldtimestamp=" + oldTimestamp + ", newtimestamp=" + newTimestamp : "") +
        (oldLength != newLength ? ", oldlength=" + oldLength + ", length=" + newLength : "")
      );
    }
    myEvents.add(new VFileContentChangeEvent(null, file, file.getModificationStamp(), -1, oldTimestamp, newTimestamp, oldLength, newLength, true));
  }

  void scheduleCreation(@NotNull VirtualFile parent, @NotNull String childName, boolean isDirectory) {
    if (LOG.isTraceEnabled()) LOG.trace("create parent=" + parent + " name=" + childName + " dir=" + isDirectory);
    myEvents.add(new VFileCreateEvent(null, parent, childName, isDirectory, true));
  }

  void scheduleDeletion(@Nullable VirtualFile file) {
    if (file != null) {
      if (LOG.isTraceEnabled()) LOG.trace("delete file=" + file);
      myEvents.add(new VFileDeleteEvent(null, file, true));
    }
  }

  void checkSymbolicLinkChange(VirtualFile child, String oldTarget, String currentTarget) {
    String currentVfsTarget = currentTarget != null ? FileUtil.toSystemIndependentName(currentTarget) : null;
    if (!Comparing.equal(oldTarget, currentVfsTarget)) {
      scheduleAttributeChange(child, VirtualFile.PROP_SYMLINK_TARGET, oldTarget, currentVfsTarget);
    }
  }

  void checkHiddenAttributeChange(VirtualFile child, boolean oldHidden, boolean newHidden) {
    if (oldHidden != newHidden) {
      scheduleAttributeChange(child, VirtualFile.PROP_HIDDEN, oldHidden, newHidden);
    }
  }

  void checkWritableAttributeChange(VirtualFile file, boolean oldWritable, boolean newWritable) {
    if (LOG_ATTRIBUTES.isTraceEnabled()) {
      LOG_ATTRIBUTES.trace("file=" + file + " writable vfs=" + file.isWritable() + " persistence=" + oldWritable + " real=" + newWritable);
    }
    if (oldWritable != newWritable) {
      scheduleAttributeChange(file, VirtualFile.PROP_WRITABLE, oldWritable, newWritable);
    }
  }

  void addAllEventsFrom(VfsEventGenerationHelper otherHelper) {
    myEvents.addAll(otherHelper.myEvents);
  }
}
