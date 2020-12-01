// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.vfs;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.history.VcsFileRevision;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileSystem;
import com.intellij.util.ArrayUtilRt;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.Charset;

/**
 * author: lesya
 */
public class VcsVirtualFile extends AbstractVcsVirtualFile {
  private static final Logger LOG = Logger.getInstance(VcsVirtualFile.class);

  private final VcsFileRevision myFileRevision;

  private volatile byte[] myContent;
  private volatile boolean myContentLoadFailed;
  private volatile Charset myCharset;
  private final Object LOCK = new Object();

  public VcsVirtualFile(@NotNull String path,
                        @Nullable VcsFileRevision revision,
                        @NotNull VirtualFileSystem fileSystem) {
    super(path, fileSystem);
    myFileRevision = revision;
  }

  public VcsVirtualFile(@NotNull VirtualFile parent, @NotNull String name, @Nullable VcsFileRevision revision, VirtualFileSystem fileSystem) {
    super(parent, name, fileSystem);
    myFileRevision = revision;
  }

  public VcsVirtualFile(@NotNull String path,
                        byte @NotNull [] content,
                        @Nullable String revision,
                        @NotNull VirtualFileSystem fileSystem) {
    this(path, null, fileSystem);
    myContent = content;
    setRevision(revision);
  }

  @Override
  public byte @NotNull [] contentsToByteArray() throws IOException {
    if (myContentLoadFailed) {
      return ArrayUtilRt.EMPTY_BYTE_ARRAY;
    }
    if (myContent == null) {
      loadContent();
    }
    return myContent;
  }

  private void loadContent() throws IOException {
    assert myFileRevision != null;
    if (myContent != null) return;

    try {
      byte[] content = myFileRevision.loadContent();

      synchronized (LOCK) {
        setRevision(VcsUtil.getShortRevisionString(myFileRevision.getRevisionNumber()));
        myContent = content;
        myContentLoadFailed = false;
        if (myContent != null && myContent.length !=0) {
          myCharset = new CharsetToolkit(myContent, Charset.defaultCharset(), false).guessEncoding(myContent.length);
        }
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

  @Nullable
  public VcsFileRevision getFileRevision() {
    return myFileRevision;
  }

  @NotNull
  @Override
  public Charset getCharset() {
    if (myCharset != null) return myCharset;
    return super.getCharset();
  }

  @Override
  public boolean isDirectory() {
    return false;
  }

  public String getRevision() {
    if (myRevision == null) {
      try {
        loadContent();
      }
      catch (IOException e) {
        LOG.info(e);
      }
    }
    return myRevision;
  }
}
