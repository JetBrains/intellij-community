/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.execution.testframework.sm.runner.history.actions;

import com.intellij.execution.TestStateStorage;
import com.intellij.execution.testframework.sm.TestHistoryConfiguration;
import com.intellij.execution.testframework.sm.runner.SMTRunnerConsoleProperties;
import com.intellij.execution.testframework.sm.runner.ui.SMTestRunnerResultsForm;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
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
  private String myFileName;
  
  public ImportTestsFromHistoryAction(@Nullable SMTRunnerConsoleProperties properties, Project project, String name) {
    super(properties, getPresentableText(project, name), getPresentableText(project, name), getIcon(project, name));
    myFileName = name;
  }

  private static Icon getIcon(Project project, String name) {
    return TestHistoryConfiguration.getInstance(project).getIcon(name);
  }

  private static String getPresentableText(Project project, String name) {
    String nameWithoutExtension = FileUtil.getNameWithoutExtension(name);
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

  @Nullable
  @Override
  public VirtualFile getFile(@NotNull Project project) {
    return LocalFileSystem.getInstance().findFileByPath(TestStateStorage.getTestHistoryRoot(project).getPath() + "/" + myFileName);
  }
}
