// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.testframework.sm.runner.history.actions;

import com.intellij.execution.Executor;
import com.intellij.execution.TestStateStorage;
import com.intellij.execution.testframework.sm.TestHistoryConfiguration;
import com.intellij.execution.testframework.sm.runner.ui.SMTestRunnerResultsForm;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.text.DateFormatUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class ImportTestsFromHistoryAction extends AbstractImportTestsAction {
  private final String myFileName;

  public ImportTestsFromHistoryAction(Project project, String name) {
    this(project, name, null);
  }

  public ImportTestsFromHistoryAction(Project project, String name, @Nullable Executor executor) {
    super(StringUtil.escapeMnemonics(getPresentableText(project, name)), getPresentableText(project, name), getIcon(project, name), executor);
    myFileName = name;
  }

  private static Icon getIcon(Project project, String name) {
    return TestHistoryConfiguration.getInstance(project).getIcon(name);
  }

  private static @NotNull @NlsSafe String getPresentableText(Project project, String name) {
    String nameWithoutExtension = FileUtilRt.getNameWithoutExtension(name);
    final int lastIndexOf = nameWithoutExtension.lastIndexOf(" - ");
    if (lastIndexOf > 0) {
      final String date = nameWithoutExtension.substring(lastIndexOf + 3);
      try {
        final Date creationDate = new SimpleDateFormat(SMTestRunnerResultsForm.HISTORY_DATE_FORMAT).parse(date);
        final String configurationName = TestHistoryConfiguration.getInstance(project).getConfigurationName(name);
        return (configurationName != null ? configurationName : nameWithoutExtension.substring(0, lastIndexOf)) +
               " (" + DateFormatUtil.formatDateTime(creationDate) + ")";
      }
      catch (ParseException ignore) {}
    }
    return nameWithoutExtension;
  }

  @Override
  protected @Nullable VirtualFile getFile(@NotNull Project project) {
    return LocalFileSystem.getInstance().refreshAndFindFileByPath(TestStateStorage.getTestHistoryRoot(project).getPath() + "/" + myFileName);
  }
}
