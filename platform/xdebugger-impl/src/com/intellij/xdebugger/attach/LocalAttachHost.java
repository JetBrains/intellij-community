// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.attach;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.BaseProcessHandler;
import com.intellij.execution.process.CapturingProcessHandler;
import com.intellij.execution.process.OSProcessUtil;
import com.intellij.execution.process.ProcessInfo;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;

public class LocalAttachHost extends EnvironmentAwareHost {
  public static final LocalAttachHost INSTANCE = new LocalAttachHost();

  @Override
  public @NotNull List<ProcessInfo> getProcessList() {
    return Arrays.asList(OSProcessUtil.getProcessList());
  }

  @Override
  public @NotNull BaseProcessHandler getProcessHandler(@NotNull GeneralCommandLine commandLine)
    throws ExecutionException {
    return new CapturingProcessHandler(commandLine);
  }

  @Override
  public @Nullable InputStream getFileContent(@NotNull String filePath) throws IOException {
    VirtualFile file = LocalFileSystem.getInstance().findFileByPath(filePath);
    if (file == null) {
      return null;
    }

    return file.getInputStream();
  }

  @Override
  public boolean canReadFile(@NotNull String filePath) {
    return new File(filePath).canRead();
  }

  @Override
  public @NotNull String getFileSystemHostId() {
    return "";
  }
}
