// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.impl;

import com.intellij.openapi.util.io.FileAttributes;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.NewVirtualFileSystem;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.io.URLUtil;
import com.intellij.util.system.OS;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class FsRoot extends VirtualDirectoryImpl {
  private final String pathWithOneTrailingSlash;

  public static @NotNull FsRoot create(int id,
                                       @NotNull VfsData.Segment segment,
                                       @NotNull NewVirtualFileSystem fileSystem,
                                       @NotNull String pathBeforeSlash,
                                       @NotNull FileAttributes fileAttributes,
                                       boolean offlineByDefault,
                                       @NotNull String originalDebugPath) throws VfsData.FileAlreadyCreatedException {
    if (!looksCanonical(pathBeforeSlash)) {
      throw new IllegalArgumentException(
        "path must be canonical but got: '" + pathBeforeSlash + "'. FS: " + fileSystem + "; attributes(flags): " + fileAttributes + "; " +
        "original path: '" + originalDebugPath + "'; " + OS.CURRENT
      );
    }

    VfsData.DirectoryData directoryData = new VfsData.DirectoryData();
    FsRoot root = new FsRoot(id, segment, directoryData, fileSystem, pathBeforeSlash);
    directoryData.assignDirectory(root);

    segment.setFlags(id, ALL_FLAGS_MASK, VfsDataFlags.toFlags(fileAttributes, offlineByDefault));
    //TODO RC: WRITABLE, HIDDEN, SPECIAL, SYMLINK flags are set 'false' by the legacy version.
    //         Was it intentional, or just an omission?
    //segment.setFlag(id, VfsDataFlags.IS_WRITABLE_FLAG, false);
    //segment.setFlag(id, VfsDataFlags.IS_HIDDEN_FLAG, false);
    //segment.setFlag(id, VfsDataFlags.IS_SPECIAL_FLAG, false);
    //segment.setFlag(id, VfsDataFlags.IS_SYMLINK_FLAG, false);

    //publish directoryData only _after_ all the initialization is done:
    segment.initFileData(id, directoryData, /*parent: */ null);

    return root;
  }

  private FsRoot(int id,
                 @NotNull VfsData.Segment segment,
                 @NotNull VfsData.DirectoryData directoryData,
                 @NotNull NewVirtualFileSystem fileSystem,
                 @NotNull String pathBeforeSlash) {
    super(id, segment, directoryData, /*parent: */ null, fileSystem);

    pathWithOneTrailingSlash = pathBeforeSlash + '/';
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
    return pathWithOneTrailingSlash;
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
      if ((i == 0 || pathBeforeSlash.charAt(i - 1) == '/') // /..
          && (i == pathBeforeSlash.length() - 2 || pathBeforeSlash.charAt(i + 2) == '/')) { // ../
        return false;
      }
      start = i + 1;
    }
    return true;
  }
}
