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
package com.intellij.openapi.fileChooser.ex;

import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.util.Icons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.io.FileFilter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class LocalFsFinder implements FileLookup.Finder, FileLookup {

  public LookupFile find(@NotNull final String path) {
    final VirtualFile byUrl = VirtualFileManager.getInstance().findFileByUrl(path);
    if (byUrl != null) return getLookupFile(path, byUrl);

    String toFind = normalize(path);
    if (toFind.length() == 0) {
      File[] roots = File.listRoots();
      if (roots.length > 0) {
        toFind = roots[0].getAbsolutePath();
      }
    }
    final File file = new File(toFind);
    return file.isAbsolute() ? getLookupFile(path, LocalFileSystem.getInstance().findFileByIoFile(file)) : null;
  }

  private LookupFile getLookupFile(final String path, final VirtualFile vFile) {
    return vFile != null ? new VfsFile(this, vFile) : new IoFile(new File(path));
  }

  public String normalize(@NotNull final String path) {
    final File file = new File(path);
    if (file.isAbsolute()) return file.getAbsolutePath();

    return new File(System.getProperty("user.home"), path).getAbsolutePath();
  }

  public String getSeparator() {
    return File.separator;
  }

  public static class FileChooserFilter implements LookupFilter {

    private final FileChooserDescriptor myDescriptor;
    private final boolean myShowHidden;

    public FileChooserFilter(final FileChooserDescriptor descriptor, boolean showHidden) {
      myDescriptor = descriptor;
      myShowHidden = showHidden;
    }

    public boolean isAccepted(final LookupFile file) {
      VirtualFile vFile = ((VfsFile)file).getFile();
      if (vFile == null) return false;
      return myDescriptor.isFileVisible(vFile, myShowHidden);
    }
  }

  public static class VfsFile implements LookupFile {
    private final VirtualFile myFile;
    private final LocalFsFinder myFinder;

    private String myMacro;

    public VfsFile(LocalFsFinder finder, final VirtualFile file) {
      myFinder = finder;
      myFile = file;
    }

    public String getName() {
      if (myFile.getParent() == null && myFile.getName().length() == 0) return "/";
      return myFile.getName();
    }

    public boolean isDirectory() {
      return myFile != null ? myFile.isDirectory() : false;
    }

    public void setMacro(final String macro) {
      myMacro = macro;
    }

    public String getMacro() {
      return myMacro;
    }

    public LookupFile getParent() {
      return myFile != null && myFile.getParent() != null ? new VfsFile(myFinder, myFile.getParent()) : null;
    }

    public String getAbsolutePath() {
      if (myFile.getParent() == null && myFile.getName().length() == 0) return "/";
      return myFile.getPresentableUrl();
    }

    public List<LookupFile> getChildren(final LookupFilter filter) {
      List<LookupFile> result = new ArrayList<LookupFile>();
      if (myFile == null) return result;

      VirtualFile[] kids = myFile.getChildren();
      for (VirtualFile each : kids) {
        LookupFile eachFile = myFinder.getLookupFile(each.getPath(), each);
        if (eachFile != null && filter.isAccepted(eachFile)) {
          result.add(eachFile);
        }
      }
      Collections.sort(result, new Comparator<LookupFile>() {
        public int compare(LookupFile o1, LookupFile o2) {
          return FileUtil.comparePaths(o1.getName(), o2.getName());
        }
      });

      return result;
    }

    public VirtualFile getFile() {
      return myFile;
    }

    public boolean exists() {
      return myFile.exists();
    }

    @Nullable
    public Icon getIcon() {
      return myFile != null ? (myFile.isDirectory() ? Icons.DIRECTORY_CLOSED_ICON : myFile.getIcon()) : null;
    }

    public boolean equals(final Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      final VfsFile vfsFile = (VfsFile)o;

      if (myFile != null ? !myFile.equals(vfsFile.myFile) : vfsFile.myFile != null) return false;

      return true;
    }

    public int hashCode() {
      return (myFile != null ? myFile.hashCode() : 0);
    }
  }

  public static class IoFile extends VfsFile {
    private final File myIoFile;

    public IoFile(final File ioFile) {
      super(null, null);
      myIoFile = ioFile;
    }

    public String getName() {
      return myIoFile.getName();
    }

    public boolean isDirectory() {
      return myIoFile != null ? myIoFile.isDirectory() : false;
    }

    public LookupFile getParent() {
      return myIoFile != null && myIoFile.getParentFile() != null ? new IoFile(myIoFile.getParentFile()) : null;
    }

    public String getAbsolutePath() {
      return myIoFile.getAbsolutePath();
    }

    public List<LookupFile> getChildren(final LookupFilter filter) {
      List<LookupFile> result = new ArrayList<LookupFile>();
      File[] files = myIoFile.listFiles(new FileFilter() {
        public boolean accept(final File pathname) {
          return filter.isAccepted(new IoFile(pathname));
        }
      });
      if (files == null) return result;

      for (File each : files) {
        result.add(new IoFile(each));
      }
      Collections.sort(result, new Comparator<LookupFile>() {
        public int compare(LookupFile o1, LookupFile o2) {
          return FileUtil.comparePaths(o1.getName(), o2.getName());
        }
      });

      return result;
    }

    public boolean exists() {
      return myIoFile.exists();
    }

    public boolean equals(final Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      final IoFile ioFile = (IoFile)o;

      if (myIoFile != null ? !myIoFile.equals(ioFile.myIoFile) : ioFile.myIoFile != null) return false;

      return true;
    }

    public int hashCode() {
      return (myIoFile != null ? myIoFile.hashCode() : 0);
    }
  }
}
