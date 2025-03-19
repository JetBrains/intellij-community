// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution;

import com.intellij.util.io.IdeUtilIoBundle;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;

@ApiStatus.Internal
public class WorkingDirectoryNotFoundException extends ExecutionException {
  public final Path workingDirectory;

  public WorkingDirectoryNotFoundException(@NotNull Path workingDirectory) {
    super(IdeUtilIoBundle.message("run.configuration.error.working.directory.does.not.exist", workingDirectory));
    this.workingDirectory = workingDirectory;
  }
}
