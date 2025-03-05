// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.encoding.EncodingManager;
import com.intellij.openapi.vfs.encoding.EncodingProjectManager;
import com.intellij.util.PathUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.nio.charset.Charset;

public class RemoteFilePath implements FilePath {
  private final @NotNull String myPath;
  private final boolean myIsDirectory;

  public RemoteFilePath(@NotNull String path, boolean isDirectory) {
    myPath = path;
    myIsDirectory = isDirectory;
  }

  @Override
  public @Nullable VirtualFile getVirtualFile() {
    return null;
  }

  @Override
  public @Nullable VirtualFile getVirtualFileParent() {
    return null;
  }

  @Override
  public @NotNull File getIOFile() {
    return new File(myPath);
  }

  @Override
  public @NotNull String getName() {
    return PathUtil.getFileName(myPath);
  }

  @Override
  public @NotNull String getPresentableUrl() {
    return getPath();
  }

  @Override
  public @NotNull Charset getCharset() {
    return getCharset(null);
  }

  @Override
  public @NotNull Charset getCharset(@Nullable Project project) {
    EncodingManager em = project == null ? EncodingManager.getInstance() : EncodingProjectManager.getInstance(project);
    return em.getDefaultCharset();
  }

  @Override
  public @NotNull FileType getFileType() {
    return FileTypeManager.getInstance().getFileTypeByFileName(getName());
  }

  @Override
  public @NotNull String getPath() {
    return myPath;
  }

  @Override
  public boolean isDirectory() {
    return myIsDirectory;
  }

  @Override
  public boolean isUnder(@NotNull FilePath parent, boolean strict) {
    return FileUtil.isAncestor(parent.getPath(), getPath(), strict);
  }

  @Override
  public @Nullable FilePath getParentPath() {
    String parent = PathUtil.getParentPath(myPath);
    return parent.isEmpty() ? null : new RemoteFilePath(parent, true);
  }

  @Override
  public boolean isNonLocal() {
    return true;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    RemoteFilePath other = (RemoteFilePath)o;
    return myIsDirectory == other.myIsDirectory && myPath.equals(other.myPath);
  }

  @Override
  public int hashCode() {
    return 31 * myPath.hashCode() + (myIsDirectory ? 1 : 0);
  }
}
