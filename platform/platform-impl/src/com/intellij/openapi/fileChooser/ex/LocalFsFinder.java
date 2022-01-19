// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileChooser.ex;

import com.intellij.ide.presentation.VirtualFilePresentation;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileSystemTree;
import com.intellij.openapi.fileChooser.ex.FileLookup.Finder;
import com.intellij.openapi.fileChooser.ex.FileLookup.LookupFile;
import com.intellij.openapi.fileChooser.ex.FileLookup.LookupFilter;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.RefreshQueue;
import com.intellij.util.PlatformIcons;
import com.intellij.util.SystemProperties;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BooleanSupplier;

public class LocalFsFinder implements Finder {
  private File myBaseDir = new File(SystemProperties.getUserHome());

  @Override
  public LookupFile find(@NotNull String path) {
    VirtualFile byUrl = VirtualFileManager.getInstance().findFileByUrl(path);
    if (byUrl != null) {
      return new VfsFile(byUrl);
    }

    String toFind = normalize(path);
    if (toFind.isEmpty()) {
      File[] roots = File.listRoots();
      if (roots.length > 0) {
        toFind = roots[0].getAbsolutePath();
      }
    }
    File file = new File(toFind);
    // '..' and '.' path components will be eliminated
    VirtualFile vFile = LocalFileSystem.getInstance().findFileByIoFile(file);
    if (vFile != null) {
      return new VfsFile(vFile);
    }
    if (file.isAbsolute()) {
      return new IoFile(new File(path));
    }
    return null;
  }

  @Override
  public String normalize(@NotNull String path) {
    File file = new File(FileUtil.expandUserHome(path));
    if (file.isAbsolute()) return file.getAbsolutePath();
    if (myBaseDir != null) return new File(myBaseDir, path).getAbsolutePath();
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
    private final BooleanSupplier myShowHidden;

    public FileChooserFilter(FileChooserDescriptor descriptor, boolean showHidden) {
      myShowHidden = () -> showHidden;
      myDescriptor = descriptor;
    }

    public FileChooserFilter(FileChooserDescriptor descriptor, FileSystemTree tree) {
      myDescriptor = descriptor;
      myShowHidden = () -> tree.areHiddensShown();
    }

    @Override
    public boolean isAccepted(LookupFile file) {
      VirtualFile vFile = ((VfsFile)file).getFile();
      return vFile != null && myDescriptor.isFileVisible(vFile, myShowHidden.getAsBoolean());
    }
  }

  public static final class VfsFile implements LookupFile {
    private final VirtualFile myFile;
    private String myMacro;

    /** @deprecated please use {@link #VfsFile(VirtualFile)} instead */
    @Deprecated(forRemoval = true)
    @ApiStatus.ScheduledForRemoval(inVersion = "2023.1")
    public VfsFile(@SuppressWarnings("unused") LocalFsFinder finder, VirtualFile file) {
      this(file);
    }

    public VfsFile(VirtualFile file) {
      myFile = file;
      if (file != null) RefreshQueue.getInstance().refresh(true, false, null, file);
    }

    @Override
    public String getName() {
      return myFile.getParent() == null && myFile.getName().length() == 0 ? "/" : myFile.getName();
    }

    @Override
    public boolean isDirectory() {
      return myFile != null && myFile.isDirectory();
    }

    @Override
    public void setMacro(String macro) {
      myMacro = macro;
    }

    @Override
    public String getMacro() {
      return myMacro;
    }

    @Override
    public LookupFile getParent() {
      return myFile != null && myFile.getParent() != null ? new VfsFile(myFile.getParent()) : null;
    }

    @Override
    public String getAbsolutePath() {
      return myFile.getParent() == null && myFile.getName().length() == 0 ? "/" : myFile.getPresentableUrl();
    }

    @Override
    public List<LookupFile> getChildren(LookupFilter filter) {
      List<LookupFile> result = new ArrayList<>();
      if (myFile == null) return result;

      VirtualFile[] kids = myFile.getChildren();
      for (VirtualFile each : kids) {
        LookupFile eachFile = new VfsFile(each);
        if (filter.isAccepted(eachFile)) {
          result.add(eachFile);
        }
      }
      result.sort((o1, o2) -> StringUtil.compare(o1.getName(), o2.getName(), !myFile.isCaseSensitive()));

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
    public @Nullable Icon getIcon() {
      return myFile == null ? null : myFile.isDirectory() ? PlatformIcons.FOLDER_ICON : VirtualFilePresentation.getIcon(myFile);
    }

    @Override
    public boolean equals(Object o) {
      return this == o || o != null && getClass() == o.getClass() && Objects.equals(myFile, ((VfsFile)o).myFile);
    }

    @Override
    public int hashCode() {
      return myFile != null ? myFile.hashCode() : 0;
    }
  }

  public static final class IoFile implements LookupFile {
    private final File myIoFile;
    private String myMacro;

    public IoFile(File ioFile) {
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
    public void setMacro(String macro) {
      myMacro = macro;
    }

    @Override
    public @Nullable String getMacro() {
      return myMacro;
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
    public List<LookupFile> getChildren(LookupFilter filter) {
      List<LookupFile> result = new ArrayList<>();
      File[] files = myIoFile.listFiles();
      if (files == null) return result;

      for (File each : files) {
        IoFile file = new IoFile(each);
        if (filter.isAccepted(file)) {
          result.add(file);
        }
      }
      result.sort((o1, o2) -> FileUtil.comparePaths(o1.getName(), o2.getName()));

      return result;
    }

    @Override
    public boolean exists() {
      return myIoFile.exists();
    }

    @Override
    public @Nullable Icon getIcon() {
      return null;
    }

    @Override
    public boolean equals(Object o) {
      return this == o || o != null && getClass() == o.getClass() && Objects.equals(myIoFile, ((IoFile)o).myIoFile);
    }

    @Override
    @SuppressWarnings("FileEqualsUsage")
    public int hashCode() {
      return myIoFile != null ? myIoFile.hashCode() : 0;
    }
  }
}
