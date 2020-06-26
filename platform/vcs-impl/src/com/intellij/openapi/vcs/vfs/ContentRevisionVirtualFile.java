// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.vcs.vfs;

import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.ByteBackedContentRevision;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * @author yole
 */
public final class ContentRevisionVirtualFile extends AbstractVcsVirtualFile {
  @NotNull private final ContentRevision myContentRevision;

  private volatile byte[] myContent;
  private volatile boolean myContentLoadFailed;
  private final Object LOCK = new Object();

  private static final Map<ContentRevision, ContentRevisionVirtualFile> ourMap = ContainerUtil.createWeakMap();

  public static ContentRevisionVirtualFile create(@NotNull ContentRevision contentRevision) {
    synchronized(ourMap) {
      ContentRevisionVirtualFile revisionVirtualFile = ourMap.get(contentRevision);
      if (revisionVirtualFile == null) {
        revisionVirtualFile = new ContentRevisionVirtualFile(contentRevision);
        ourMap.put(contentRevision, revisionVirtualFile);
      }
      return revisionVirtualFile;
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
      byte[] bytes;
      if (myContentRevision instanceof ByteBackedContentRevision) {
        bytes = ((ByteBackedContentRevision)myContentRevision).getContentAsBytes();
      }
      else {
        final String content = myContentRevision.getContent();
        bytes = content != null ? content.getBytes(getCharset()) : null;
      }

      if (bytes == null) {
        throw new VcsException("Could not load content");
      }

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
}