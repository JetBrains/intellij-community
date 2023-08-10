// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.testframework.sm.runner.history.actions;

import com.intellij.execution.TestStateStorage;
import com.intellij.execution.runners.PreferredPlace;
import com.intellij.execution.runners.RunTab;
import com.intellij.execution.testframework.sm.SmRunnerBundle;
import com.intellij.execution.testframework.sm.TestHistoryConfiguration;
import com.intellij.execution.testframework.sm.runner.SMTRunnerConsoleProperties;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class ImportTestsGroup extends ActionGroup {
  private SMTRunnerConsoleProperties myProperties;
  public ImportTestsGroup() {
    super(() -> SmRunnerBundle.message("sm.test.runner.import.test.group.history"),
          () -> SmRunnerBundle.message("sm.test.runner.import.test.group.open.recent.session"), AllIcons.Vcs.History);
    setPopup(true);
    getTemplatePresentation().putClientProperty(RunTab.PREFERRED_PLACE, PreferredPlace.TOOLBAR);
  }

  public ImportTestsGroup(SMTRunnerConsoleProperties properties) {
    this();
    myProperties = properties;
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public AnAction @NotNull [] getChildren(@Nullable AnActionEvent e) {
    if (e == null) return EMPTY_ARRAY;
    final Project project = e.getProject();
    if (project == null) return EMPTY_ARRAY;
    final Collection<String> filePaths = TestHistoryConfiguration.getInstance(project).getFiles();
    final File testHistoryRoot = TestStateStorage.getTestHistoryRoot(project);
    final List<File> fileNames = filePaths.stream()
      .map(fileName -> new File(testHistoryRoot, fileName))
      .filter(file -> file.exists())
      .sorted(Comparator.comparingLong(File::lastModified).reversed())
      .toList();
    final int historySize = fileNames.size();
    final AnAction[] actions = new AnAction[historySize];
    for (int i = 0; i < historySize; i++) {
      actions[i] = new ImportTestsFromHistoryAction(project, fileNames.get(i).getName(), myProperties != null ? myProperties.getExecutor() : null);
    }
    return actions;
  }
}
