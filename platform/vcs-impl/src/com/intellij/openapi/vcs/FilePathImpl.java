// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.Strings;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.encoding.EncodingManager;
import com.intellij.openapi.vfs.encoding.EncodingProjectManager;
import com.intellij.util.PathUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.nio.charset.Charset;

/**
 * It is kept for compatibility reasons: some plugins still refer this obsolete class.
 *
 * @deprecated Use {@link LocalFilePath} instead.
 */
@Deprecated
public final class FilePathImpl implements FilePath {
  private final @NotNull String myPath;
  private final boolean myIsDirectory;

  public FilePathImpl(@NotNull String path, boolean isDirectory) {
    myPath = FileUtil.toCanonicalPath(path);
    myIsDirectory = isDirectory;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    FilePathImpl path = (FilePathImpl)o;

    if (myIsDirectory != path.myIsDirectory) return false;
    if (!(SystemInfoRt.isFileSystemCaseSensitive ? myPath.equals(path.myPath) : myPath.equalsIgnoreCase(path.myPath))) {
      return false;
    }

    return true;
  }

  @Override
  public int hashCode() {
    int result = SystemInfoRt.isFileSystemCaseSensitive ? myPath.hashCode() : Strings.stringHashCodeInsensitive(myPath);
    result = 31 * result + (myIsDirectory ? 1 : 0);
    return result;
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
    return parent.isEmpty() ? null : new FilePathImpl(parent, true);
  }

  @Override
  public @Nullable VirtualFile getVirtualFile() {
    return LocalFileSystem.getInstance().findFileByPath(myPath);
  }

  @Override
  public @Nullable VirtualFile getVirtualFileParent() {
    FilePath parent = getParentPath();
    return parent != null ? parent.getVirtualFile() : null;
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
    return FileUtil.toSystemDependentName(myPath);
  }

  @Override
  public @NotNull Charset getCharset() {
    return getCharset(null);
  }

  @Override
  public @NotNull Charset getCharset(@Nullable Project project) {
    VirtualFile file = getVirtualFile();
    String path = myPath;
    while ((file == null || !file.isValid()) && !path.isEmpty()) {
      path = PathUtil.getParentPath(path);
      file = LocalFileSystem.getInstance().findFileByPath(path);
    }
    if (file != null) {
      return file.getCharset();
    }
    EncodingManager e = project == null ? EncodingManager.getInstance() : EncodingProjectManager.getInstance(project);
    return e.getDefaultCharset();
  }

  @Override
  public @NotNull FileType getFileType() {
    VirtualFile file = getVirtualFile();
    FileTypeManager manager = FileTypeManager.getInstance();
    return file != null ? manager.getFileTypeByFile(file) : manager.getFileTypeByFileName(getName());
  }

  @Override
  public @NonNls String toString() {
    return myPath + (myIsDirectory ? "/" : "");
  }

  @Override
  public boolean isNonLocal() {
    return false;
  }
}
