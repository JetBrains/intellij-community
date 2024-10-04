// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.diff.impl.patch.formove;

import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.changes.patch.RelativePathCalculator;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@ApiStatus.Internal
public final class PathMerger {
  private PathMerger() {
  }

  @Nullable
  public static VirtualFile getFile(final VirtualFile base, final String path) {
    return getFile(new VirtualFilePathMerger(base), path);
  }

  @Nullable
  public static VirtualFile getFile(final VirtualFile base, final String path, final List<? super String> tail) {
    return getFile(new VirtualFilePathMerger(base), path, tail);
  }

  @Nullable
  public static File getFile(final File base, final String path) {
    return getFile(new IoFilePathMerger(base), path);
  }

  @Nullable
  public static File getFile(final File base, final String path, final List<? super String> tail) {
    return getFile(new IoFilePathMerger(base), path, tail);
  }

  @Nullable
  public static FilePath getFile(final FilePath base, final String path) {
    return getFile(new FilePathPathMerger(base), path);
  }

  @Nullable
  public static FilePath getFile(final FilePath base, final String path, final List<? super String> tail) {
    return getFile(new FilePathPathMerger(base), path, tail);
  }

  @Nullable
  public static <T> T getFile(final FilePathMerger<T> merger, final String path) {
    if (path == null) {
      return null;
    }
    final List<String> tail = new ArrayList<>();
    final T file = getFile(merger, path, tail);
    if (tail.isEmpty()) {
      return file;
    }
    return null;
  }

  @Nullable
  public static <T> T getFile(final FilePathMerger<T> merger, final String path, final List<? super String> tail) {
    final String[] pieces = RelativePathCalculator.split(path);

    for (int i = 0; i < pieces.length; i++) {
      final String piece = pieces[i];
      if ("".equals(piece) || ".".equals(piece)) {
        continue;
      }
      if ("..".equals(piece)) {
        final boolean upResult = merger.up();
        if (! upResult) return null;
        continue;
      }

      final boolean downResult = merger.down(piece);
      if (! downResult) {
        if (tail != null) {
          tail.addAll(Arrays.asList(pieces).subList(i, pieces.length));
        }
        return merger.getResult();
      }
    }

    return merger.getResult();
  }

  @Nullable
  public static VirtualFile getBase(final VirtualFile base, final String path) {
    return getBase(new VirtualFilePathMerger(base), path);
  }

  @Nullable
  public static <T> T getBase(final FilePathMerger<T> merger, final String path) {
    final boolean caseSensitive = SystemInfo.isFileSystemCaseSensitive;
    final String[] parts = path.replace("\\", "/").split("/");
    for (int i = parts.length - 1; i >=0; --i) {
      final String part = parts[i];
      if ("".equals(part) || ".".equals(part)) {
        continue;
      } else if ("..".equals(part)) {
        if (! merger.up()) return null;
        continue;
      }
      final String vfName = merger.getCurrentName();
      if (vfName == null) return null;
      if ((caseSensitive && vfName.equals(part)) || ((! caseSensitive) && vfName.equalsIgnoreCase(part))) {
        if (! merger.up()) return null;
      } else {
        return null;
      }
    }
    return merger.getResult();
  }

  public static class VirtualFilePathMerger implements FilePathMerger<VirtualFile> {
    private VirtualFile myCurrent;

    public VirtualFilePathMerger(final VirtualFile current) {
      myCurrent = current;
    }

    @Override
    public boolean up() {
      myCurrent = myCurrent.getParent();
      return myCurrent != null;
    }

    @Override
    public boolean down(final String name) {
      VirtualFile nextChild = myCurrent.findChild(name);
      if (nextChild == null) {
        nextChild = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(new File(myCurrent.getPath(), name));
      }
      if (nextChild != null) {
        myCurrent = nextChild;
        return true;
      }
      return false;
    }

    @Override
    public VirtualFile getResult() {
      return myCurrent;
    }

    @Override
    public String getCurrentName() {
      return myCurrent == null ? null : myCurrent.getName();
    }
  }

  // ! does not check result for existence!
  public static class IoFilePathMerger implements FilePathMerger<File> {
    private File myBase;
    private final List<String> myChildPathElements;

    public IoFilePathMerger(final File base) {
      myBase = base;
      myChildPathElements = new ArrayList<>();
    }

    @Override
    public boolean up() {
      if (! myChildPathElements.isEmpty()) {
        myChildPathElements.remove(myChildPathElements.size() - 1);
        return true;
      }
      myBase = myBase.getParentFile();
      return myBase != null;
    }

    @Override
    public boolean down(String name) {
      myChildPathElements.add(name);
      return true;
    }

    @Override
    public File getResult() {
      final StringBuilder sb = new StringBuilder();
      for (String element : myChildPathElements) {
        if (sb.length() > 0) {
          sb.append(File.separatorChar);
        }
        sb.append(element);
      }
      return new File(myBase, sb.toString());
    }

    @Override
    @Nullable
    public String getCurrentName() {
      if (! myChildPathElements.isEmpty()) {
        return myChildPathElements.get(myChildPathElements.size() - 1);
      }
      return myBase == null ? null : myBase.getName();
    }
  }

  public static class FilePathPathMerger implements FilePathMerger<FilePath> {
    private final IoFilePathMerger myIoDelegate;
    private boolean myIsDirectory;

    public FilePathPathMerger(final FilePath base) {
      myIoDelegate = new IoFilePathMerger(base.getIOFile());
    }

    @Override
    public boolean down(String name) {
      return myIoDelegate.down(name);
    }

    @Override
    public boolean up() {
      return myIoDelegate.up();
    }

    @Override
    public FilePath getResult() {
      return VcsUtil.getFilePath(myIoDelegate.getResult(), myIsDirectory);
    }

    @Override
    public String getCurrentName() {
      return myIoDelegate.getCurrentName();
    }

    public void setIsDirectory(boolean isDirectory) {
      myIsDirectory = isDirectory;
    }
  }

  public interface FilePathMerger<T> {
    boolean up();

    /**
     * !!! should not go down (to null state), if can't find corresponding child
     */
    boolean down(final String name);
    T getResult();
    @Nullable
    String getCurrentName();
  }
}
