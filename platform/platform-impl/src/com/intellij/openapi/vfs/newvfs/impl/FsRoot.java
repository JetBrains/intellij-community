// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs.newvfs.impl;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.NewVirtualFileSystem;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.io.URLUtil;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NotNull;

public class FsRoot extends VirtualDirectoryImpl {
  private final String myPathWithOneSlash;

  public FsRoot(int id,
                int nameId,
                @NotNull VfsData vfsData,
                @NotNull NewVirtualFileSystem fs,
                @NotNull String pathBeforeSlash) throws VfsData.FileAlreadyCreatedException {
    super(id, vfsData.getSegment(id, true), new VfsData.DirectoryData(), null, fs);
    if (!looksCanonical(pathBeforeSlash)) {
      throw new IllegalArgumentException("path must be canonical but got: '" + pathBeforeSlash + "'");
    }
    myPathWithOneSlash = pathBeforeSlash + '/';
    VfsData.initFile(id, mySegment, nameId, myData);
  }

  @Override
  protected char @NotNull [] appendPathOnFileSystem(int pathLength, int[] position) {
    int myLength = myPathWithOneSlash.length() - 1;
    char[] chars = new char[pathLength + myLength];
    CharArrayUtil.getChars(myPathWithOneSlash, chars, 0, position[0], myLength);
    position[0] += myLength;
    return chars;
  }

  @Override
  public void setNewName(@NotNull String newName) {
    throw new IncorrectOperationException();
  }

  @Override
  public final void setParent(@NotNull VirtualFile newParent) {
    throw new IncorrectOperationException();
  }

  @NotNull
  @Override
  public String getPath() {
    return myPathWithOneSlash;
  }

  @NotNull
  @Override
  public String getUrl() {
    return getFileSystem().getProtocol() + URLUtil.SCHEME_SEPARATOR + getPath();
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
