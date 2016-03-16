/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.vcs;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
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
 * @deprecated Use {@link LocalFilePath} instead. To remove in IDEA 16.
 */
@SuppressWarnings("unused")
@Deprecated
public class FilePathImpl implements FilePath {
  @NotNull private final String myPath;
  private final boolean myIsDirectory;

  public FilePathImpl(@NotNull String path, boolean isDirectory) {
    myPath = FileUtil.toCanonicalPath(path);
    myIsDirectory = isDirectory;
  }
  public FilePathImpl(@NotNull VirtualFile file) {
    this(file.getPath(), file.isDirectory());
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    FilePathImpl path = (FilePathImpl)o;

    if (myIsDirectory != path.myIsDirectory) return false;
    if (!FileUtil.PATH_HASHING_STRATEGY.equals(myPath, path.myPath)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = FileUtil.PATH_HASHING_STRATEGY.computeHashCode(myPath);
    result = 31 * result + (myIsDirectory ? 1 : 0);
    return result;
  }

  @Override
  public void refresh() {
  }

  @Override
  public void hardRefresh() {
    LocalFileSystem.getInstance().refreshAndFindFileByPath(myPath);
  }

  @NotNull
  @Override
  public String getPath() {
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
  @Nullable
  public FilePath getParentPath() {
    String parent = PathUtil.getParentPath(myPath);
    return parent.isEmpty() ? null : new FilePathImpl(parent, true);
  }

  @Override
  @Nullable
  public VirtualFile getVirtualFile() {
    return LocalFileSystem.getInstance().findFileByPath(myPath);
  }

  @Override
  @Nullable
  public VirtualFile getVirtualFileParent() {
    FilePath parent = getParentPath();
    return parent != null ? parent.getVirtualFile() : null;
  }

  @Override
  @NotNull
  public File getIOFile() {
    return new File(myPath);
  }

  @NotNull
  @Override
  public String getName() {
    return PathUtil.getFileName(myPath);
  }

  @NotNull
  @Override
  public String getPresentableUrl() {
    return FileUtil.toSystemDependentName(myPath);
  }

  @Override
  @Nullable
  public Document getDocument() {
    VirtualFile file = getVirtualFile();
    if (file == null || file.getFileType().isBinary()) {
      return null;
    }
    return FileDocumentManager.getInstance().getDocument(file);
  }

  @Override
  @NotNull
  public Charset getCharset() {
    return getCharset(null);
  }

  @Override
  @NotNull
  public Charset getCharset(@Nullable Project project) {
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
  @NotNull
  public FileType getFileType() {
    VirtualFile file = getVirtualFile();
    FileTypeManager manager = FileTypeManager.getInstance();
    return file != null ? manager.getFileTypeByFile(file) : manager.getFileTypeByFileName(getName());
  }

  @Override
  @NonNls
  public String toString() {
    return myPath + (myIsDirectory ? "/" : "");
  }

  @Override
  public boolean isNonLocal() {
    return false;
  }
}
