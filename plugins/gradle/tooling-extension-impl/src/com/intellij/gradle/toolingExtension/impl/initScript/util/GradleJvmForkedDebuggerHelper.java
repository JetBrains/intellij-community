// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.initScript.util;

import com.intellij.gradle.toolingExtension.impl.util.GradleTaskUtil;
import com.intellij.openapi.externalSystem.rt.execution.ForkedDebuggerHelper;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.tasks.TaskCollection;
import org.gradle.api.tasks.testing.Test;
import org.gradle.process.JavaForkOptions;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class GradleJvmForkedDebuggerHelper {

  public static TaskCollection<Task> getTasksToDebug(Project project) {
    boolean isDebugAllEnabled = GradleDebuggerUtil.isDebugAllEnabled();
    project.getLogger().debug("isDebugAllEnabled: {}", isDebugAllEnabled);

    return (isDebugAllEnabled ? project.getTasks() : GradleTasksUtil.getStartTasks(project))
      .matching(task -> task instanceof JavaForkOptions);
  }

  public static void setupDebugger(Task task, String projectDirectory) {
    if (GradleDebuggerUtil.isDebuggerEnabled()) {
      if (task instanceof Test) {
        ((Test)task).setMaxParallelForks(1);
        GradleTaskUtil.setTaskTestForkEvery(((Test)task), 0);
      }

      String processName = task.getPath();
      String debuggerId = GradleDebuggerUtil.getDebuggerId();
      String processParameters = GradleDebuggerUtil.getProcessParameters();
      int debugPort = ForkedDebuggerHelper.setupDebugger(debuggerId, processName, processParameters, projectDirectory);

      List<String> jvmArgs = new ArrayList<>(Objects.requireNonNull(((JavaForkOptions)task).getJvmArgs()));
      jvmArgs.removeIf(it -> it == null || it.startsWith("-agentlib:jdwp") || it.startsWith("-Xrunjdwp"));
      jvmArgs.add(ForkedDebuggerHelper.JVM_DEBUG_SETUP_PREFIX + ForkedDebuggerHelper.getAddrFromProperty() + ':' + debugPort);
      jvmArgs.addAll(GradleDebuggerUtil.getProcessOptions());
      ((JavaForkOptions)task).setJvmArgs(jvmArgs);
    }
  }

  public static void signalizeFinish(Task task) {
    if (GradleDebuggerUtil.isDebuggerEnabled()) {
      String processName = task.getPath();
      String debuggerId = GradleDebuggerUtil.getDebuggerId();
      ForkedDebuggerHelper.signalizeFinish(debuggerId, processName);
    }
  }
}