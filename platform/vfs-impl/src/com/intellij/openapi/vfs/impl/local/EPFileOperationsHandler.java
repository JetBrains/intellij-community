// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.impl.local;

import com.intellij.openapi.vfs.LocalFileOperationsHandler;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ThrowableConsumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;

import static com.intellij.openapi.vfs.impl.local.LocalFileSystemBase.FILE_OPERATIONS_HANDLER_EP_NAME;

public class EPFileOperationsHandler implements LocalFileOperationsHandler {
  @Override
  public boolean delete(@NotNull VirtualFile file) throws IOException {
    for (LocalFileOperationsHandler handler : FILE_OPERATIONS_HANDLER_EP_NAME.getExtensionList()) {
      if (handler.delete(file)) return true;
    }
    return false;
  }

  @Override
  public boolean move(@NotNull VirtualFile file, @NotNull VirtualFile toDir) throws IOException {
    for (LocalFileOperationsHandler handler : FILE_OPERATIONS_HANDLER_EP_NAME.getExtensionList()) {
      if (handler.move(file, toDir)) return true;
    }
    return false;
  }

  @Override
  public @Nullable File copy(@NotNull VirtualFile file, @NotNull VirtualFile toDir, @NotNull String copyName) throws IOException {
    for (LocalFileOperationsHandler handler : FILE_OPERATIONS_HANDLER_EP_NAME.getExtensionList()) {
      File copy = handler.copy(file, toDir, copyName);
      if (copy != null) return copy;
    }
    return null;
  }

  @Override
  public boolean rename(@NotNull VirtualFile file, @NotNull String newName) throws IOException {
    for (LocalFileOperationsHandler handler : FILE_OPERATIONS_HANDLER_EP_NAME.getExtensionList()) {
      if (handler.rename(file, newName)) return true;
    }
    return false;
  }

  @Override
  public boolean createFile(@NotNull VirtualFile dir, @NotNull String name) throws IOException {
    for (LocalFileOperationsHandler handler : FILE_OPERATIONS_HANDLER_EP_NAME.getExtensionList()) {
      if (handler.createFile(dir, name)) return true;
    }
    return false;
  }

  @Override
  public boolean createDirectory(@NotNull VirtualFile dir, @NotNull String name) throws IOException {
    for (LocalFileOperationsHandler handler : FILE_OPERATIONS_HANDLER_EP_NAME.getExtensionList()) {
      if (handler.createDirectory(dir, name)) return true;
    }
    return false;
  }

  @Override
  public void afterDone(@NotNull ThrowableConsumer<LocalFileOperationsHandler, IOException> invoker) {
    for (LocalFileOperationsHandler handler : FILE_OPERATIONS_HANDLER_EP_NAME.getExtensionList()) {
      handler.afterDone(invoker);
    }
  }
}
