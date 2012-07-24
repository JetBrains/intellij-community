/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.fileTypes.UnknownFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.encoding.EncodingManager;
import com.intellij.openapi.vfs.encoding.EncodingProjectManager;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.lang.reflect.Constructor;
import java.nio.charset.Charset;

public class FilePathImpl implements FilePath {
  private VirtualFile myVirtualFile;
  private VirtualFile myVirtualParent;
  private final String myName;
  @NotNull private final File myFile;
  private boolean myIsDirectory;
  private final boolean myLocal;

  private FilePathImpl(VirtualFile virtualParent,
                       @NotNull String name,
                       final boolean isDirectory,
                       VirtualFile child,
                       final boolean forDeleted) {
    myLocal = true;
    myVirtualParent = virtualParent;
    myName = name;
    myIsDirectory = isDirectory;
    if (myVirtualParent == null) {
      myFile = new File(myName);
    }
    else {
      myFile = new File(new File(myVirtualParent.getPath()), myName);
    }

    if (!forDeleted) {
      if (child == null) {
        refresh();
      }
      else {
        myVirtualFile = child;
      }
    }
  }

  private void detectCharset() {
    VirtualFile file = myVirtualFile;
    if (file == null || !file.isValid() || file.isDirectory()) return;
    FileType fileType = file.getFileType();
    if (fileType == UnknownFileType.INSTANCE) {
      FileTypeRegistry.getInstance().detectFileTypeFromContent(file);
    }
  }

  @Heavy
  public FilePathImpl(VirtualFile virtualParent, String name, final boolean isDirectory) {
    this(virtualParent, name, isDirectory, null, false);
  }

  @Heavy
  private FilePathImpl(VirtualFile virtualParent, String name, final boolean isDirectory, final boolean forDeleted) {
    this(virtualParent, name, isDirectory, null, forDeleted);
  }

  public FilePathImpl(@NotNull File file, final boolean isDirectory) {
    this(file, isDirectory, true);
  }
  private FilePathImpl(@NotNull File file, final boolean isDirectory, boolean local) {
    myFile = file;
    myName = file.getName();
    myIsDirectory = isDirectory;
    myLocal = local;
  }

  public FilePathImpl(@NotNull VirtualFile virtualFile) {
    this(virtualFile.getParent(), virtualFile.getName(), virtualFile.isDirectory(), virtualFile, false);
  }

  public FilePath createChild(final String subPath, final boolean isDirectory) {
    if (StringUtil.isEmptyOrSpaces(subPath)) return this;

    if (getVirtualFile() != null && subPath.indexOf('/') == -1 && subPath.indexOf('\\') == -1) {
      return new FilePathImpl(getVirtualFile(), subPath, isDirectory, true);
    }
    else {
      return new FilePathImpl(new File(getIOFile(), subPath), isDirectory);
    }
  }

  public int hashCode() {
    return StringUtil.stringHashCodeInsensitive(myFile.getPath());
  }

  public boolean equals(Object o) {
    if (!(o instanceof FilePath)) {
      return false;
    }
    else {
      if (! isSpecialName(myName) && ! isSpecialName(((FilePath)o).getName()) &&
          ! Comparing.equal(myName, ((FilePath)o).getName())) return false;
      return myFile.equals(((FilePath)o).getIOFile());
    }
  }
  
  private static boolean isSpecialName(final String name) {
    return ".".equals(name) || "..".equals(name);
  }

  @Override
  public void refresh() {
    if (myLocal) {
      if (myVirtualParent == null) {
        myVirtualFile = LocalFileSystem.getInstance().findFileByIoFile(myFile);
      }
      else {
        myVirtualFile = myVirtualParent.findChild(myName);
      }
    }
  }

  @Override
  public void hardRefresh() {
    if (myLocal && (myVirtualFile == null || ! myVirtualFile.isValid())) {
      myVirtualFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(myFile);
    }
  }

  @Override
  public String getPath() {
    final VirtualFile virtualFile = myVirtualFile;
    if (virtualFile != null && virtualFile.isValid()) {
      return virtualFile.getPath();
    }
    else {
      return myFile.getPath();
    }
  }

  public void setIsDirectory(boolean isDirectory) {
    myIsDirectory = isDirectory;
  }

  @Override
  public boolean isDirectory() {
    if (myVirtualFile == null) {
      return myIsDirectory;
    }
    else {
      return myVirtualFile.isDirectory();
    }
  }

  @Override
  public boolean isUnder(FilePath parent, boolean strict) {
    if (myVirtualFile != null && parent.getVirtualFile() != null) {
      return VfsUtil.isAncestor(parent.getVirtualFile(), myVirtualFile, strict);
    }
    return FileUtil.isAncestor(parent.getIOFile(), getIOFile(), strict);
  }

  @Override
  public FilePath getParentPath() {
    if (myVirtualParent != null && myVirtualParent.isValid()) {
      return new FilePathImpl(myVirtualParent);
    }

    // can't use File.getParentPath() because the path may not correspond to an actual file on disk,
    // and adding a drive letter would not be appropriate (IDEADEV-7405)
    // path containing exactly one separator is assumed to be root path
    final String path = myFile.getPath();
    int pos = path.lastIndexOf(File.separatorChar);
    if (pos < 0 || pos == path.indexOf(File.separatorChar)) {
      return null;
    }
    return new FilePathImpl(new File(path.substring(0, pos)), true);
  }

  @Override
  @Nullable
  public VirtualFile getVirtualFile() {
    if (myVirtualFile != null && !myVirtualFile.isValid()) {
      myVirtualFile = null;
    }
    detectCharset();
    return myVirtualFile;
  }

  @Override
  @Nullable
  public VirtualFile getVirtualFileParent() {
    if (myVirtualParent != null && !myVirtualParent.isValid()) {
      myVirtualParent = null;
    }
    return myVirtualParent;
  }

  @Override
  @NotNull
  public File getIOFile() {
    return myFile;
  }

  @Override
  public String getName() {
    return myName;
  }

  @Override
  public String getPresentableUrl() {
    if (myVirtualFile == null) {
      return myFile.getAbsolutePath();
    }
    else {
      return myVirtualFile.getPresentableUrl();
    }
  }

  @Override
  @Nullable
  public Document getDocument() {
    if (myVirtualFile == null || myVirtualFile.getFileType().isBinary()) {
      return null;
    }
    return FileDocumentManager.getInstance().getDocument(myVirtualFile);
  }

  @Override
  public Charset getCharset() {
    return getCharset(null);
  }

  @Override
  public Charset getCharset(Project project) {
    // try to find existing virtual file
    VirtualFile existing = myVirtualFile != null && myVirtualFile.isValid() ? myVirtualFile : null;
    if (existing == null) {
      LocalFileSystem lfs = LocalFileSystem.getInstance();
      for (File f = myFile; f != null; f = f.getParentFile()) {
        existing = lfs.findFileByIoFile(f);
        if (existing != null && existing.isValid()) {
          break;
        }
      }
    }
    if (existing != null) {
      Charset rc = existing.getCharset();
      if (rc != null) {
        return rc;
      }
    }
    EncodingManager e = project != null ? EncodingProjectManager.getInstance(project) : null;
    if (e == null) {
      e = EncodingManager.getInstance();
    }
    return e.getDefaultCharset();
  }

  @Override
  public FileType getFileType() {
    return myVirtualFile != null ? myVirtualFile.getFileType() : FileTypeManager.getInstance().getFileTypeByFileName(myFile.getName());
  }

  public static FilePathImpl create(File selectedFile) {
    return create(selectedFile, false);
  }

  public static FilePathImpl create(File selectedFile, boolean isDirectory) {
    if (selectedFile == null) {
      return null;
    }

    LocalFileSystem lfs = LocalFileSystem.getInstance();

    VirtualFile virtualFile = lfs.findFileByIoFile(selectedFile);
    if (virtualFile != null) {
      return new FilePathImpl(virtualFile);
    }

    return createForDeletedFile(selectedFile, isDirectory);
  }

  public static FilePathImpl createForDeletedFile(final File selectedFile, final boolean isDirectory) {
    LocalFileSystem lfs = LocalFileSystem.getInstance();

    File parentFile = selectedFile.getParentFile();
    if (parentFile == null) {
      return new FilePathImpl(selectedFile, isDirectory);
    }

    VirtualFile virtualFileParent = lfs.findFileByIoFile(parentFile);
    if (virtualFileParent != null) {
      return new FilePathImpl(virtualFileParent, selectedFile.getName(), isDirectory, true);
    }
    else {
      return new FilePathImpl(selectedFile, isDirectory);
    }
  }

  public static FilePath createOn(String s) {
    File ioFile = new File(s);
    final LocalFileSystem localFileSystem = LocalFileSystem.getInstance();
    VirtualFile virtualFile = localFileSystem.findFileByIoFile(ioFile);
    if (virtualFile != null) {
      return new FilePathImpl(virtualFile);
    }
    else {
      VirtualFile virtualFileParent = localFileSystem.findFileByIoFile(ioFile.getParentFile());
      if (virtualFileParent != null) {
        return new FilePathImpl(virtualFileParent, ioFile.getName(), false);
      }
      else {
        return null;
      }
    }
  }

  private static Constructor<File> ourFileStringConstructor;
  private static boolean ourFileStringConstructorInitialized;

  @NotNull
  public static FilePath createNonLocal(String path, final boolean directory) {
    path = path.replace('/', File.separatorChar);
    // avoid filename normalization (IDEADEV-10548)
    if (!ourFileStringConstructorInitialized) {
      ourFileStringConstructorInitialized = true;
      try {
        ourFileStringConstructor = File.class.getDeclaredConstructor(String.class, int.class);
        ourFileStringConstructor.setAccessible(true);
      }
      catch (Exception ex) {
        ourFileStringConstructor = null;
      }
    }
    File file = null;
    try {
      if (ourFileStringConstructor != null) {
        file = ourFileStringConstructor.newInstance(path, 1);
      }
    }
    catch (Exception ex) {
      // reflection call failed, try regular call
    }
    if (file == null) {
      file = new File(path);
    }
    return new FilePathImpl(file, directory, false);
  }

  @Override
  @NonNls
  public String toString() {
    return "FilePath[" + myFile + "]";
  }

  @Override
  public boolean isNonLocal() {
    return !myLocal;
  }
}
