// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileChooser.ex;

import com.intellij.execution.wsl.WSLDistribution;
import com.intellij.execution.wsl.WSLUtil;
import com.intellij.execution.wsl.WslDistributionManager;
import com.intellij.ide.presentation.VirtualFilePresentation;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.ex.FileLookup.Finder;
import com.intellij.openapi.fileChooser.ex.FileLookup.LookupFile;
import com.intellij.openapi.fileChooser.ex.FileLookup.LookupFilter;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.NioFiles;
import com.intellij.openapi.util.text.StringUtil;
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
import java.io.IOError;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

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
    catch (InvalidPathException ignored) {
      if (isIncompleteUnc(toFind) && WSLUtil.isSystemCompatible()) {
        return toFind.equals("\\\\") ? WslTopFile.TOP : WslTopFile.INCOMPLETE;
      }
    }

    return null;
  }

  @Override
  public String normalize(@NotNull String path) {
    try {
      Path file = Path.of(FileUtil.expandUserHome(path));
      if (!file.isAbsolute() && myBaseDir != null) {
        file = myBaseDir.resolve(path).toAbsolutePath();
      }
      return trimUncRoot(file.toString());
    }
    catch (InvalidPathException ignored) { }
    catch (IOError e) {
      Logger.getInstance(LocalFsFinder.class).info("path=" + path + "; base=" + myBaseDir, e);
    }
    return path;
  }

  @Override
  public String getSeparator() {
    return File.separator;
  }

  @Override
  public @NotNull List<String> split(@NotNull String path) {
    try {
      Path pathObj = Path.of(normalize(path));
      List<String> result = new ArrayList<>(pathObj.getNameCount() + 1);
      result.add(trimUncRoot(pathObj.getRoot().toString()));
      for (Path part : pathObj) result.add(part.toString());
      return result;
    }
    catch (InvalidPathException e) {
      return isIncompleteUnc(path) ? List.of(trimUncRoot(path)) : Finder.super.split(path);
    }
  }

  private static boolean isIncompleteUnc(String path) {
    if (path.startsWith("\\\\")) {
      int p = path.indexOf('\\', 2);
      return p < 0 || p == path.length() - 1;
    }
    return false;
  }

  private static String trimUncRoot(String path) {
    return path.startsWith("\\\\") ? StringUtil.trimTrailing(path, '\\') : path;
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
      if (file instanceof VfsFile) {
        VirtualFile vFile = ((VfsFile)file).getFile();
        return vFile != null && myDescriptor.isFileVisible(vFile, myShowHidden);
      }
      else {
        return false;
      }
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

    /** @deprecated please use {@link #IoFile(Path)} instead */
    @Deprecated
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
      return trimUncRoot(NioFiles.getFileName(myFile));
    }

    @Override
    public boolean isDirectory() {
      return Files.isDirectory(myFile);
    }

    @Override
    public LookupFile getParent() {
      Path parent = myFile.getParent();
      if (parent != null) {
        return new IoFile(parent);
      }
      else if (myFile.toString().startsWith("\\\\")) {
        return WslTopFile.TOP;
      }
      else {
        return null;
      }
    }

    @Override
    public String getAbsolutePath() {
      return trimUncRoot(myFile.toAbsolutePath().toString());
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
    public Icon getIcon() {
      return Files.isDirectory(myFile) ? PlatformIcons.FOLDER_ICON
                                       : FileTypeRegistry.getInstance().getFileTypeByFileName(myFile.toString()).getIcon();
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

  private static final class WslTopFile extends LookupFileWithMacro {
    private static final WslTopFile TOP = new WslTopFile(true);
    private static final WslTopFile INCOMPLETE = new WslTopFile(false);

    private final boolean myExists;

    private WslTopFile(boolean exists) {
      myExists = exists;
    }

    @Override
    public String getName() {
      return "";
    }

    @Override
    public String getAbsolutePath() {
      return "";
    }

    @Override
    public boolean isDirectory() {
      return true;
    }

    @Override
    public List<LookupFile> getChildren(LookupFilter filter) {
      try {
        List<WSLDistribution> vms = WslDistributionManager.getInstance().getInstalledDistributionsFuture().get(200, TimeUnit.MILLISECONDS);
        List<LookupFile> result = new ArrayList<>(vms.size());
        for (WSLDistribution vm : vms) {
          IoFile file = new IoFile(vm.getUNCRootPath());
          if (filter.isAccepted(file)) {
            result.add(file);
          }
        }
        result.sort((o1, o2) -> StringUtil.compare(o1.getName(), o2.getName(), false));
        return result;
      }
      catch (Exception ignore) {
        return List.of();
      }
    }

    @Override
    public @Nullable LookupFile getParent() {
      return null;
    }

    @Override
    public boolean exists() {
      return myExists;
    }

    @Override
    public Icon getIcon() {
      return PlatformIcons.FOLDER_ICON;
    }
  }
}
