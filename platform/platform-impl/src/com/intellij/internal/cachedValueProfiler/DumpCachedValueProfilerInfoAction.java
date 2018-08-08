// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.cachedValueProfiler;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.util.CachedValueProfiler;
import com.intellij.testFramework.LightVirtualFile;
import org.jetbrains.annotations.NotNull;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;

import static java.time.temporal.ChronoField.*;

public class DumpCachedValueProfilerInfoAction extends DumbAwareAction {
  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    if (project == null) return;

    dumpResults(project);
  }

  static void dumpResults(@NotNull Project project) {
    CachedValueProfiler profiler = CachedValueProfiler.getInstance();

    String text = profiler.dumpStorage();

    LightVirtualFile file = new LightVirtualFile(String.format("dump-%s.txt", time()), text);
    OpenFileDescriptor descriptor = new OpenFileDescriptor(project, file);
    FileEditorManager.getInstance(project).openEditor(descriptor, true);
  }

  @NotNull
  private static String time() {
    DateTimeFormatter formatter = new DateTimeFormatterBuilder()
      .appendValue(HOUR_OF_DAY, 2)
      .appendLiteral(':')
      .appendValue(MINUTE_OF_HOUR, 2)
      .optionalStart().appendLiteral(':').appendValue(SECOND_OF_MINUTE, 2)
      .appendLiteral('|')
      .appendValue(YEAR)
      .appendLiteral('-')
      .appendValue(MONTH_OF_YEAR, 2)
      .appendLiteral('-')
      .appendValue(DAY_OF_MONTH, 2)
      .toFormatter();
    return LocalDateTime.now().format(formatter);
  }
}
