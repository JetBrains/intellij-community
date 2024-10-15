// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.impl;

import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileAttributes;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.NewVirtualFileSystem;
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFS;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.io.URLUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

public final class FsRoot extends VirtualDirectoryImpl {
  private final String myPathWithOneSlash;

  @ApiStatus.Internal
  public FsRoot(int id,
                @NotNull VfsData vfsData,
                @NotNull NewVirtualFileSystem fs,
                @NotNull String pathBeforeSlash,
                @NotNull FileAttributes attributes,
                @NotNull String originalDebugPath,
                @NotNull PersistentFS persistentFs) throws VfsData.FileAlreadyCreatedException {
    super(id, vfsData.getSegment(id, true), new VfsData.DirectoryData(), null, fs);
    if (!looksCanonical(pathBeforeSlash)) {
      throw new IllegalArgumentException("path must be canonical but got: '" + pathBeforeSlash + "'. FS: " + fs + "; attributes: " + attributes + "; original path: '" + originalDebugPath + "'; " +
                                         SystemInfo.getOsNameAndVersion());
    }
    myPathWithOneSlash = pathBeforeSlash + '/';
    VfsData.Segment segment = getSegment();
    segment.initFileData(id, myData, this);
    // assume root has FS-default case-sensitivity
    segment.setFlag(id, VfsDataFlags.CHILDREN_CASE_SENSITIVE, attributes.areChildrenCaseSensitive() == FileAttributes.CaseSensitivity.SENSITIVE);
    segment.setFlag(id, VfsDataFlags.CHILDREN_CASE_SENSITIVITY_CACHED, true);
    segment.setFlag(id, VfsDataFlags.IS_OFFLINE, PersistentFS.isOfflineByDefault(persistentFs.getFileAttributes(id)));
  }

  @Override
  public void setNewName(@NotNull String newName) {
    throw new IncorrectOperationException();
  }

  @Override
  public void setParent(@NotNull VirtualFile newParent) {
    throw new IncorrectOperationException();
  }

  @Override
  public @NotNull String getPath() {
    return myPathWithOneSlash;
  }

  @Override
  public @NotNull String getUrl() {
    return getFileSystem().getProtocol() + URLUtil.SCHEME_SEPARATOR + getPath();
  }

  @Override
  public @NotNull String getPresentableName() {
    return getFileSystem().extractPresentableUrl(getName());
  }

  private static boolean looksCanonical(@NotNull String pathBeforeSlash) {
    if (pathBeforeSlash.endsWith("/")) {
      return false;
    }
    int start = 0;
    while (true) {
      int i = pathBeforeSlash.indexOf("..", start);
      if (i == -1) break;
      if ((i == 0 || pathBeforeSlash.charAt(i-1) == '/') // /..
          && (i == pathBeforeSlash.length() - 2 || pathBeforeSlash.charAt(i+2) == '/')) { // ../
        return false;
      }
      start = i+1;
    }
    return true;
  }
}
