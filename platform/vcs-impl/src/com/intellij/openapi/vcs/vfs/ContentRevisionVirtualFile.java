// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.vcs.vfs;

import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.ByteBackedContentRevision;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * @author yole
 */
public class ContentRevisionVirtualFile extends AbstractVcsVirtualFile {
  @NotNull private final ContentRevision myContentRevision;
  private byte[] myContent;
  private boolean myContentLoadFailed;

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
    setCharset(CharsetToolkit.UTF8_CHARSET);
  }

  @Override
  public boolean isDirectory() {
    return false;
  }

  @Override
  @NotNull
  public byte[] contentsToByteArray() {
    if (myContentLoadFailed) {
      return ArrayUtil.EMPTY_BYTE_ARRAY;
    }
    if (myContent == null) {
      loadContent();
    }
    return myContent;
  }

  private void loadContent() {
    try {
      byte[] bytes = null;
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

      myContent = bytes;
      setRevision(myContentRevision.getRevisionNumber().asString());
    }
    catch (VcsException e) {
      myContentLoadFailed = true;
      myContent = ArrayUtil.EMPTY_BYTE_ARRAY;
      setRevision("0");
      showLoadingContentFailedMessage(e);
    }
    catch (ProcessCanceledException ex) {
      myContent = ArrayUtil.EMPTY_BYTE_ARRAY;
    }
  }

  @NotNull
  public ContentRevision getContentRevision() {
    return myContentRevision;
  }
}