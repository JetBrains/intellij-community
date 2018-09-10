// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.vfs;

import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileSystem;
import org.jetbrains.annotations.NotNull;

public class VcsVirtualFolder extends AbstractVcsVirtualFile {
  private final VirtualFile myChild;
  public VcsVirtualFolder(String name, VirtualFile child, VirtualFileSystem fileSystem) {
    super(name == null ? "" : name, fileSystem);
    myChild = child;
  }

  @Override
  public VirtualFile[] getChildren() {
    return new VirtualFile[]{myChild};
  }

  @Override
  public boolean isDirectory() {
    return true;
  }

  @Override
  @NotNull
  public byte[] contentsToByteArray() {
    throw new RuntimeException(VcsBundle.message("exception.text.internal.error.method.should.not.be.called"));
  }
}
