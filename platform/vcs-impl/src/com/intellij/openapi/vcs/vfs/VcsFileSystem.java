// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.vfs;

import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vfs.DeprecatedVirtualFileSystem;
import com.intellij.openapi.vfs.NonPhysicalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;


public class VcsFileSystem extends DeprecatedVirtualFileSystem implements NonPhysicalFileSystem {
  /**
   * @deprecated Use {@link #getCouldNotImplementMessage()} instead
   */
  @Deprecated(forRemoval = true)
  public static final String COULD_NOT_IMPLEMENT_MESSAGE = "Could not implement"; //NON-NLS

  private static final String PROTOCOL = "vcs";  //NON-NLS

  public static VcsFileSystem getInstance() {
    return (VcsFileSystem)VirtualFileManager.getInstance().getFileSystem(PROTOCOL);
  }

  @Override
  public @NotNull String getProtocol() {
    return PROTOCOL;
  }

  @Override
  public VirtualFile findFileByPath(@NotNull String path) {
    return null;
  }

  @Override
  public void refresh(boolean asynchronous) {
  }

  @Override
  public VirtualFile refreshAndFindFileByPath(@NotNull String path) {
    return null;
  }

  @Override
  public void fireContentsChanged(Object requestor, @NotNull VirtualFile file, long oldModificationStamp) {
    super.fireContentsChanged(requestor, file, oldModificationStamp);
  }

  public static @Nls String getCouldNotImplementMessage() {
    return VcsBundle.message("exception.text.internal.errror.could.not.implement.method");
  }
}
