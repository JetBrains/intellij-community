// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.FileAttribute;
import com.intellij.openapi.vfs.newvfs.ManagingFS;
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFS;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * @author peter
 */
public final class LastUnchangedContentTracker {
  private static final Logger LOG = Logger.getInstance(LastUnchangedContentTracker.class);
  private static final Key<Long> LAST_TS_KEY = Key.create("LAST_TS_KEY");
  private static final FileAttribute LAST_TS_ATTR = new FileAttribute("LAST_TS_ATTR", 0, true);
  private static final FileAttribute ACQUIRED_CONTENT_ATTR = new FileAttribute("ACQUIRED_CONTENT_ATTR", 1, true);
  private static final Key<Boolean> VCS_INVALID_FILE_STATUS = Key.create("VCS_INVALID_FILE_STATUS");

  public static void updateLastUnchangedContent(@NotNull VirtualFile file) {
    if (isTouched(file)) {
      return;
    }

    Long lastTs = getLastSavedStamp(file);
    final long stamp = file.getTimeStamp();
    if (lastTs != null && stamp == lastTs) {
      return;
    }

    Integer oldContentId = getSavedContentId(file);
    if (oldContentId != null && oldContentId > 0) {
      if (LOG.isDebugEnabled()) {
        LOG.debug("releasing content for " + file + ", id = " + oldContentId);
      }
      getFS().releaseContent(oldContentId);
    }

    saveContentReference(file, getFS().acquireContent(file));
    markTouched(file);
  }

  private static boolean isTouched(VirtualFile file) {
    return Boolean.TRUE.equals(file.getUserData(VCS_INVALID_FILE_STATUS));
  }

  public static void markTouched(VirtualFile file) {
    file.putUserData(VCS_INVALID_FILE_STATUS, Boolean.TRUE);
  }

  public static void markUntouched(VirtualFile file) {
    file.putUserData(VCS_INVALID_FILE_STATUS, null);
  }

  public static byte @Nullable [] getLastUnchangedContent(@NotNull VirtualFile file) {
    final Integer id = getSavedContentId(file);
    try {
      return id == null ? null : getFS().contentsToByteArray(id);
    }
    catch (IOException e) {
      LOG.info(e);
    }
    return null;
  }

  private static PersistentFS getFS() {
    return (PersistentFS)ManagingFS.getInstance();
  }

  private static void saveContentReference(VirtualFile file, int contentId) {
    if (contentId == 0) {
      if (LOG.isDebugEnabled()) {
        LOG.debug("saveContentReference without content for file = " + file);
      }
      return; // content not loaded yet, nothing to save
    }

    LOG.assertTrue(contentId > 0, contentId);

    if (LOG.isDebugEnabled()) {
      LOG.debug("saveContentReference file = " + file + ", id = " + contentId);
    }

    long stamp = file.getTimeStamp();
    try {
      try (DataOutputStream contentStream = ACQUIRED_CONTENT_ATTR.writeAttribute(file)) {
        contentStream.writeInt(contentId);
      }

      try (DataOutputStream tsStream = LAST_TS_ATTR.writeAttribute(file)) {
        tsStream.writeLong(stamp);
      }

      file.putUserData(LAST_TS_KEY, stamp);
    }
    catch (IOException e) {
      LOG.info(e);
    }
  }

  public static boolean hasSavedContent(VirtualFile file) {
    return getSavedContentId(file) != null;
  }

  public static void forceSavedContent(VirtualFile file, @NotNull String content) {
    saveContentReference(file, getFS().storeUnlinkedContent(content.getBytes(file.getCharset())));
  }

    @Nullable
  private static Integer getSavedContentId(VirtualFile file) {
    if (!file.isValid()) {
      return null;
    }

    Integer oldContentId = null;
    try(final DataInputStream stream = ACQUIRED_CONTENT_ATTR.readAttribute(file)) {
      if (LOG.isDebugEnabled()) {
        LOG.debug("getSavedContentId for " + file + "; stream=" + stream);
      }
      if (stream != null) {
        oldContentId = stream.readInt();
        if (LOG.isDebugEnabled()) {
          LOG.debug("oldContentId=" + oldContentId);
        }
        LOG.assertTrue(oldContentId > 0, oldContentId);
      }
    }
    catch (IOException e) {
      LOG.info(e);
    }
    return oldContentId;
  }

  @Nullable
  private static Long getLastSavedStamp(VirtualFile file) {
    Long l = file.getUserData(LAST_TS_KEY);
    if (l == null) {
      try (final DataInputStream stream = LAST_TS_ATTR.readAttribute(file)) {
        if (stream != null) {
          l = stream.readLong();
        }
      }
      catch (IOException e) {
        LOG.info(e);
      }
    }
    return l;
  }
}
