// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileChooser.ex;

import com.intellij.ide.presentation.VirtualFilePresentation;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileSystemTree;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.RefreshQueue;
import com.intellij.util.PlatformIcons;
import com.intellij.util.SystemProperties;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class LocalFsFinder implements FileLookup.Finder, FileLookup {

  private File myBaseDir = new File(SystemProperties.getUserHome());

  @Override
  public LookupFile find(@NotNull final String path) {
    final VirtualFile byUrl = VirtualFileManager.getInstance().findFileByUrl(path);
    if (byUrl != null) {
      return new VfsFile(this, byUrl);
    }

    String toFind = normalize(path);
    if (toFind.length() == 0) {
      File[] roots = File.listRoots();
      if (roots.length > 0) {
        toFind = roots[0].getAbsolutePath();
      }
    }
    final File file = new File(toFind);
    // '..' and '.' path components will be eliminated
    VirtualFile vFile = LocalFileSystem.getInstance().findFileByIoFile(file);
    if (vFile != null) {
      return new VfsFile(this, vFile);
    } else if (file.isAbsolute()) {
      return new IoFile(new File(path));
    }
    return null;
  }

  @Override
  public String normalize(@NotNull String path) {
    path = FileUtil.expandUserHome(path);
    final File file = new File(path);
    if (file.isAbsolute()) return file.getAbsolutePath();
    if (myBaseDir != null) {
      return new File(myBaseDir, path).getAbsolutePath();
    }
    return path;
  }

  @Override
  public String getSeparator() {
    return File.separator;
  }

  public void setBaseDir(@Nullable File baseDir) {
    myBaseDir = baseDir;
  }

  public static class FileChooserFilter implements LookupFilter {

    private final FileChooserDescriptor myDescriptor;
    private final Computable<Boolean> myShowHidden;

    public FileChooserFilter(final FileChooserDescriptor descriptor, boolean showHidden) {
      myShowHidden = new Computable.PredefinedValueComputable<>(showHidden);
      myDescriptor = descriptor;
    }
    public FileChooserFilter(final FileChooserDescriptor descriptor, final FileSystemTree tree) {
      myDescriptor = descriptor;
      myShowHidden = () -> tree.areHiddensShown();
    }

    @Override
    public boolean isAccepted(final LookupFile file) {
      VirtualFile vFile = ((VfsFile)file).getFile();
      if (vFile == null) return false;
      return myDescriptor.isFileVisible(vFile, myShowHidden.compute());
    }
  }

  public static class VfsFile implements LookupFile {
    private final VirtualFile myFile;
    private final LocalFsFinder myFinder;

    private String myMacro;

    public VfsFile(LocalFsFinder finder, final VirtualFile file) {
      myFinder = finder;
      myFile = file;
      if (file != null) RefreshQueue.getInstance().refresh(true, false, null, file);
    }

    @Override
    public String getName() {
      if (myFile.getParent() == null && myFile.getName().length() == 0) return "/";
      return myFile.getName();
    }

    @Override
    public boolean isDirectory() {
      return myFile != null && myFile.isDirectory();
    }

    @Override
    public void setMacro(final String macro) {
      myMacro = macro;
    }

    @Override
    public String getMacro() {
      return myMacro;
    }

    @Override
    public LookupFile getParent() {
      return myFile != null && myFile.getParent() != null ? new VfsFile(myFinder, myFile.getParent()) : null;
    }

    @Override
    public String getAbsolutePath() {
      if (myFile.getParent() == null && myFile.getName().length() == 0) return "/";
      return myFile.getPresentableUrl();
    }

    @Override
    public List<LookupFile> getChildren(final LookupFilter filter) {
      List<LookupFile> result = new ArrayList<>();
      if (myFile == null) return result;

      VirtualFile[] kids = myFile.getChildren();
      for (VirtualFile each : kids) {
        LookupFile eachFile = new VfsFile(myFinder, each);
        if (filter.isAccepted(eachFile)) {
          result.add(eachFile);
        }
      }
      result.sort((o1, o2) -> FileUtil.comparePaths(o1.getName(), o2.getName()));

      return result;
    }

    public VirtualFile getFile() {
      return myFile;
    }

    @Override
    public boolean exists() {
      return myFile.exists();
    }

    @Override
    @Nullable
    public Icon getIcon() {
      return myFile != null ? (myFile.isDirectory() ? PlatformIcons.FOLDER_ICON : VirtualFilePresentation.getIcon(myFile)) : null;
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      final VfsFile vfsFile = (VfsFile)o;

      if (myFile != null ? !myFile.equals(vfsFile.myFile) : vfsFile.myFile != null) return false;

      return true;
    }

    @Override
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

    @Override
    public String getName() {
      return myIoFile.getName();
    }

    @Override
    public boolean isDirectory() {
      return myIoFile != null && myIoFile.isDirectory();
    }

    @Override
    public LookupFile getParent() {
      return myIoFile != null && myIoFile.getParentFile() != null ? new IoFile(myIoFile.getParentFile()) : null;
    }

    @Override
    public String getAbsolutePath() {
      return myIoFile.getAbsolutePath();
    }

    @Override
    public List<LookupFile> getChildren(final LookupFilter filter) {
      List<LookupFile> result = new ArrayList<>();
      File[] files = myIoFile.listFiles(pathname -> filter.isAccepted(new IoFile(pathname)));
      if (files == null) return result;

      for (File each : files) {
        result.add(new IoFile(each));
      }
      result.sort((o1, o2) -> FileUtil.comparePaths(o1.getName(), o2.getName()));

      return result;
    }

    @Override
    public boolean exists() {
      return myIoFile.exists();
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      final IoFile ioFile = (IoFile)o;

      if (myIoFile != null ? !myIoFile.equals(ioFile.myIoFile) : ioFile.myIoFile != null) return false;

      return true;
    }

    @Override
    public int hashCode() {
      return (myIoFile != null ? myIoFile.hashCode() : 0);
    }
  }
}
