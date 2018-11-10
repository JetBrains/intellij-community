// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

  @NotNull
  @Override
  public List<ProcessInfo> getProcessList() {
    return Arrays.asList(OSProcessUtil.getProcessList());
  }

  @NotNull
  @Override
  public BaseProcessHandler getProcessHandler(@NotNull GeneralCommandLine commandLine)
    throws ExecutionException {
    return new CapturingProcessHandler(commandLine);
  }

  @Nullable
  @Override
  public InputStream getFileContent(@NotNull String filePath) throws IOException {
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

  @NotNull
  @Override
  public String getFileSystemHostId() {
    return "";
  }
}
