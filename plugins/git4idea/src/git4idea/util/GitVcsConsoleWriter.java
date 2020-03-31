// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.util;

import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import org.jetbrains.annotations.NotNull;

import java.text.SimpleDateFormat;
import java.util.Date;

@Service
public final class GitVcsConsoleWriter {
  @NotNull
  public static GitVcsConsoleWriter getInstance(@NotNull Project project) {
    return project.getService(GitVcsConsoleWriter.class);
  }

  private static final int MAX_CONSOLE_OUTPUT_SIZE = 10000;

  private final Project myProject;

  public GitVcsConsoleWriter(@NotNull Project project) {
    myProject = project;
  }

  /**
   * Shows a plain message in the Version Control Console.
   */
  public void showMessage(@NotNull String message) {
    showMessage(message, ConsoleViewContentType.NORMAL_OUTPUT);
  }

  /**
   * Shows error message in the Version Control Console
   */
  public void showErrorMessage(@NotNull String line) {
    showMessage(line, ConsoleViewContentType.ERROR_OUTPUT);
  }

  /**
   * Shows a command line message in the Version Control Console
   */
  public void showCommandLine(@NotNull String cmdLine) {
    SimpleDateFormat f = new SimpleDateFormat("HH:mm:ss.SSS");
    showMessage(f.format(new Date()) + ": " + cmdLine, ConsoleViewContentType.SYSTEM_OUTPUT);
  }

  /**
   * Show message in the Version Control Console
   *
   * @param message     a message to show
   * @param contentType a style to use
   */
  private void showMessage(@NotNull String message, @NotNull ConsoleViewContentType contentType) {
    if (message.length() == 0) {
      return;
    }
    ProjectLevelVcsManager.getInstance(myProject).addMessageToConsoleWindow(StringUtil.shortenPathWithEllipsis(message, MAX_CONSOLE_OUTPUT_SIZE), contentType);
  }
}
