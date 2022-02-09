// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileChooser.ex;

import com.intellij.ide.presentation.VirtualFilePresentation;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.ex.FileLookup.Finder;
import com.intellij.openapi.fileChooser.ex.FileLookup.LookupFile;
import com.intellij.openapi.fileChooser.ex.FileLookup.LookupFilter;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.NioFiles;
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
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class LocalFsFinder implements Finder {
  private final boolean myUseVfs;
  private @Nullable Path myBaseDir = Path.of(SystemProperties.getUserHome());

  public LocalFsFinder() {
    this(true);
  }

  public LocalFsFinder(boolean useVfs) {
    myUseVfs = useVfs;
  }

  @Override
  public LookupFile find(@NotNull String path) {
    if (myUseVfs) {
      VirtualFile byUrl = VirtualFileManager.getInstance().findFileByUrl(path);
      if (byUrl != null) {
        return new VfsFile(byUrl);
      }
    }

    String toFind = normalize(path);
    if (toFind.isEmpty()) {
      toFind = FileSystems.getDefault().getRootDirectories().iterator().next().toString();
    }

    if (myUseVfs) {
      // '..' and '.' path components will be eliminated
      VirtualFile vFile = LocalFileSystem.getInstance().findFileByPath(toFind);
      if (vFile != null) {
        return new VfsFile(vFile);
      }
    }

    try {
      Path file = Path.of(toFind);
      if (file.isAbsolute()) {
        return new IoFile(file);
      }
    }
    catch (InvalidPathException ignored) { }

    return null;
  }

  @Override
  public String normalize(@NotNull String path) {
    try {
      Path file = Path.of(FileUtil.expandUserHome(path));
      if (file.isAbsolute()) return file.toString();
      if (myBaseDir != null) return myBaseDir.resolve(path).toAbsolutePath().toString();
    }
    catch (InvalidPathException ignored) { }
    return path;
  }

  @Override
  public String getSeparator() {
    return File.separator;
  }

  public LocalFsFinder withBaseDir(@Nullable Path baseDir) {
    myBaseDir = baseDir;
    return this;
  }

  public void setBaseDir(@Nullable File baseDir) {
    withBaseDir(baseDir != null ? baseDir.toPath() : null);
  }

  public static final class FileChooserFilter implements LookupFilter {
    private final FileChooserDescriptor myDescriptor;
    private final boolean myShowHidden;

    public FileChooserFilter(FileChooserDescriptor descriptor, boolean showHidden) {
      myShowHidden = showHidden;
      myDescriptor = descriptor;
    }

    @Override
    public boolean isAccepted(LookupFile file) {
      VirtualFile vFile = ((VfsFile)file).getFile();
      return vFile != null && myDescriptor.isFileVisible(vFile, myShowHidden);
    }
  }

  private static abstract class LookupFileWithMacro implements LookupFile {
    private String myMacro;

    @Override
    public final void setMacro(String macro) {
      myMacro = macro;
    }

    @Override
    public final String getMacro() {
      return myMacro;
    }
  }

  public static final class VfsFile extends LookupFileWithMacro {
    private final VirtualFile myFile;

    /** @deprecated please use {@link #VfsFile(VirtualFile)} instead */
    @Deprecated(forRemoval = true)
    @ApiStatus.ScheduledForRemoval(inVersion = "2023.1")
    public VfsFile(@SuppressWarnings("unused") LocalFsFinder finder, VirtualFile file) {
      this(file);
    }

    public VfsFile(@NotNull VirtualFile file) {
      myFile = file;
      RefreshQueue.getInstance().refresh(true, false, null, file);
    }

    public VirtualFile getFile() {
      return myFile;
    }

    @Override
    public String getName() {
      return myFile.getParent() == null && myFile.getName().isEmpty() ? "/" : myFile.getName();
    }

    @Override
    public boolean isDirectory() {
      return myFile.isDirectory();
    }

    @Override
    public LookupFile getParent() {
      VirtualFile parent = myFile.getParent();
      return parent != null ? new VfsFile(parent) : null;
    }

    @Override
    public String getAbsolutePath() {
      return myFile.getParent() == null && myFile.getName().length() == 0 ? "/" : myFile.getPresentableUrl();
    }

    @Override
    public List<LookupFile> getChildren(LookupFilter filter) {
      VirtualFile[] kids = myFile.getChildren();
      List<LookupFile> result = new ArrayList<>(kids.length);
      for (VirtualFile each : kids) {
        LookupFile eachFile = new VfsFile(each);
        if (filter.isAccepted(eachFile)) {
          result.add(eachFile);
        }
      }
      result.sort((o1, o2) -> StringUtil.compare(o1.getName(), o2.getName(), !myFile.isCaseSensitive()));

      return result;
    }

    @Override
    public boolean exists() {
      return myFile.exists();
    }

    @Override
    public Icon getIcon() {
      return myFile.isDirectory() ? PlatformIcons.FOLDER_ICON : VirtualFilePresentation.getIcon(myFile);
    }

    @Override
    public boolean equals(Object o) {
      return this == o || o != null && getClass() == o.getClass() && myFile.equals(((VfsFile)o).myFile);
    }

    @Override
    public int hashCode() {
      return myFile.hashCode();
    }
  }

  public static final class IoFile extends LookupFileWithMacro {
    private final Path myFile;

    public IoFile(@NotNull File file) {
      this(file.toPath());
    }

    public IoFile(@NotNull Path file) {
      myFile = file;
    }

    public Path getFile() {
      return myFile;
    }

    @Override
    public String getName() {
      return NioFiles.getFileName(myFile);
    }

    @Override
    public boolean isDirectory() {
      return Files.isDirectory(myFile);
    }

    @Override
    public LookupFile getParent() {
      Path parent = myFile.getParent();
      return parent != null ? new IoFile(parent) : null;
    }

    @Override
    public String getAbsolutePath() {
      return myFile.toAbsolutePath().toString();
    }

    @Override
    public List<LookupFile> getChildren(LookupFilter filter) {
      List<Path> files = NioFiles.list(myFile);
      List<LookupFile> result = new ArrayList<>(files.size());
      if (files.isEmpty()) return result;

      for (Path each : files) {
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
      return Files.exists(myFile);
    }

    @Override
    public @Nullable Icon getIcon() {
      return null;
    }

    @Override
    public boolean equals(Object o) {
      return this == o || o != null && getClass() == o.getClass() && myFile.equals(((IoFile)o).myFile);
    }

    @Override
    public int hashCode() {
      return myFile.hashCode();
    }
  }
}
