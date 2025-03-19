// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.attach.fs;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileListener;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.VirtualFileSystem;
import com.intellij.util.IncorrectOperationException;
import com.intellij.xdebugger.attach.EnvironmentAwareHost;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * A file system capable of lazily loading the contents of remote host (see {@link EnvironmentAwareHost#getFileContent(String)})
 * to step into, pause, set breakpoints, etc etc. during debugging.
 */
public final class LazyAttachVirtualFS extends VirtualFileSystem {
  private static final Logger LOG = Logger.getInstance(LazyAttachVirtualFS.class);

  private static final @NonNls String PROTOCOL = "lazyAttachVfs";

  private final Map<String, LazyAttachVirtualFile> myFileCache = new HashMap<>();

  public static LazyAttachVirtualFS getInstance() {
    return (LazyAttachVirtualFS)VirtualFileManager.getInstance().getFileSystem(PROTOCOL);
  }

  @Override
  public @NotNull String getProtocol() {
    return PROTOCOL;
  }

  public @Nullable VirtualFile findFileByPath(@NotNull String path, @NotNull EnvironmentAwareHost hostInfo) {
    final String fullFilePath = hostInfo.getFileSystemHostId() + path;

    return myFileCache.computeIfAbsent(fullFilePath, s -> {
      String content;
      try {
        content = getFileContent(hostInfo, path);
      }
      catch (IOException e) {
        LOG.warn("can't read file", e);
        return null;
      }

      if (content == null) {
        return null;
      }

      return new LazyAttachVirtualFile(fullFilePath, content);
    });
  }

  @Override
  public VirtualFile findFileByPath(@NotNull String path) {
    return myFileCache.get(path);
  }

  @Override
  public void refresh(boolean asynchronous) {
    throw new IncorrectOperationException();
  }

  @Override
  public VirtualFile refreshAndFindFileByPath(@NotNull String path) {
    return null;
  }

  @Override
  public void addVirtualFileListener(@NotNull VirtualFileListener listener) {
  }

  @Override
  public void removeVirtualFileListener(@NotNull VirtualFileListener listener) {
  }

  @Override
  protected void deleteFile(Object requestor, @NotNull VirtualFile vFile) {
    throw new IncorrectOperationException();
  }

  @Override
  protected void moveFile(Object requestor, @NotNull VirtualFile vFile, @NotNull VirtualFile newParent) {
    throw new IncorrectOperationException();
  }

  @Override
  protected void renameFile(Object requestor, @NotNull VirtualFile vFile, @NotNull String newName) {
    throw new IncorrectOperationException();
  }

  @Override
  protected @NotNull VirtualFile createChildFile(Object requestor, @NotNull VirtualFile vDir, @NotNull String fileName) {
    throw new IncorrectOperationException();
  }

  @Override
  protected @NotNull VirtualFile createChildDirectory(Object requestor, @NotNull VirtualFile vDir, @NotNull String dirName) {
    throw new IncorrectOperationException();
  }

  @Override
  protected @NotNull VirtualFile copyFile(Object requestor,
                                          @NotNull VirtualFile virtualFile,
                                          @NotNull VirtualFile newParent,
                                          @NotNull String copyName) {
    throw new IncorrectOperationException();
  }

  @Override
  public boolean isReadOnly() {
    return true;
  }

  private static @Nullable String getFileContent(@NotNull EnvironmentAwareHost host, @NotNull String path) throws IOException {
    InputStream stream = host.getFileContent(path);
    if (stream == null) {
      return null;
    }

    return new String(FileUtil.loadBytes(stream), StandardCharsets.UTF_8);
  }
}
