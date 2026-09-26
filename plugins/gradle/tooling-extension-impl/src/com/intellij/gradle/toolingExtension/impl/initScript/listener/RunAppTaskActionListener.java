// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.initScript.listener;

import org.gradle.api.Task;
import org.gradle.api.execution.TaskActionListener;
import org.gradle.api.tasks.JavaExec;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;

@SuppressWarnings("IO_FILE_USAGE")
public abstract class RunAppTaskActionListener implements TaskActionListener {

  private final String myTaskName;
  private File myClasspathFile;

  public RunAppTaskActionListener(String taskName) {
    myTaskName = taskName;
  }

  @Override
  public void beforeActions(@NotNull Task task) {
    if (task instanceof JavaExec && task.getName().equals(myTaskName)) {
      try {
        myClasspathFile = patchTaskClasspath((JavaExec)task);
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
  }

  @Override
  public void afterActions(@NotNull Task task) {
    if (myClasspathFile != null) {
      myClasspathFile.delete();
    }
  }

  public abstract File patchTaskClasspath(JavaExec task) throws IOException;
}
