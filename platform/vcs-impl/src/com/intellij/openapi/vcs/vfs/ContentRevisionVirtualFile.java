// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.vcs.vfs;

import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.ChangesUtil;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.history.ShortVcsRevisionNumber;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.nio.charset.StandardCharsets;
import java.util.Set;


public final class ContentRevisionVirtualFile extends AbstractVcsVirtualFile {
  @NotNull private final ContentRevision myContentRevision;

  private volatile byte[] myContent;
  private volatile boolean myContentLoadFailed;
  private final Object LOCK = new Object();

  private static final Set<ContentRevisionVirtualFile> ourCache = ContainerUtil.createWeakSet();

  public static ContentRevisionVirtualFile create(@NotNull ContentRevision contentRevision) {
    synchronized (ourCache) {
      for (ContentRevisionVirtualFile file : ourCache) {
        if (contentRevision.equals(file.getContentRevision())) return file;
      }
      ContentRevisionVirtualFile file = new ContentRevisionVirtualFile(contentRevision);
      ourCache.add(file);
      return file;
    }
  }

  private ContentRevisionVirtualFile(@NotNull ContentRevision contentRevision) {
    super(contentRevision.getFile().getPath(), VcsFileSystem.getInstance());
    myContentRevision = contentRevision;
    setCharset(StandardCharsets.UTF_8);
  }

  @Override
  public boolean isDirectory() {
    return false;
  }

  @Override
  public byte @NotNull [] contentsToByteArray() {
    if (myContentLoadFailed) {
      return ArrayUtilRt.EMPTY_BYTE_ARRAY;
    }
    if (myContent == null) {
      loadContent();
    }
    return myContent;
  }

  private void loadContent() {
    try {
      byte[] bytes = ChangesUtil.loadContentRevision(myContentRevision);

      synchronized (LOCK) {
        myContent = bytes;
        myContentLoadFailed = false;
        setRevision(myContentRevision.getRevisionNumber().asString());
      }
    }
    catch (VcsException e) {
      synchronized (LOCK) {
        myContentLoadFailed = true;
        myContent = ArrayUtilRt.EMPTY_BYTE_ARRAY;
        setRevision("0");
      }
      showLoadingContentFailedMessage(e);
    }
  }

  @NotNull
  public ContentRevision getContentRevision() {
    return myContentRevision;
  }

  @Nls
  @Override
  protected @NotNull String getPresentableName(@Nls @NotNull String baseName) {
    VcsRevisionNumber number = getContentRevision().getRevisionNumber();
    if (number instanceof ShortVcsRevisionNumber) {
      return baseName + " (" + ((ShortVcsRevisionNumber) number).toShortString() + ")";
    }
    return super.getPresentableName(baseName);
  }
}