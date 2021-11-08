// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.util;

import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsConsoleLine;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Service
public final class GitVcsConsoleWriter {
  private static final Logger LOG = Logger.getInstance(GitVcsConsoleWriter.class);

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
  public void showMessage(@NotNull @NlsSafe String message) {
    showMessage(message, ConsoleViewContentType.NORMAL_OUTPUT);
  }

  /**
   * Shows error message in the Version Control Console
   */
  public void showErrorMessage(@NotNull @NlsSafe String line) {
    showMessage(line, ConsoleViewContentType.ERROR_OUTPUT);
  }

  /**
   * Shows a command line message in the Version Control Console
   */
  public void showCommandLine(@NotNull @NlsSafe String cmdLine) {
    SimpleDateFormat f = new SimpleDateFormat("HH:mm:ss.SSS");
    showMessage(f.format(new Date()) + ": " + cmdLine, ConsoleViewContentType.SYSTEM_OUTPUT);
  }

  /**
   * Show message in the Version Control Console
   *
   * @param message     a message to show
   * @param contentType a style to use
   */
  public void showMessage(@NotNull @NlsSafe String message, @NotNull ConsoleViewContentType contentType) {
    String shortMessage = StringUtil.shortenPathWithEllipsis(message, MAX_CONSOLE_OUTPUT_SIZE);
    showMessage(VcsConsoleLine.create(shortMessage, contentType));
  }

  public void showMessage(@NotNull List<Pair<String, Key>> lineChunks) {
    int totalLength = 0;
    for (Pair<String, Key> chunk : lineChunks) {
      totalLength += chunk.first.length();
    }

    int prefixEnd = (int)(MAX_CONSOLE_OUTPUT_SIZE * 0.3);
    int suffixStart = totalLength - (int)(MAX_CONSOLE_OUTPUT_SIZE * 0.7);
    boolean useEllipsis = totalLength > MAX_CONSOLE_OUTPUT_SIZE * 1.2;

    int index = 0;
    List<Pair<String, ConsoleViewContentType>> messages = new ArrayList<>();
    for (Pair<String, Key> chunk : lineChunks) {
      String message = chunk.first;
      if (message.isEmpty()) continue;

      ConsoleViewContentType type = ConsoleViewContentType.getConsoleViewType(chunk.second);

      if (useEllipsis) {
        TextRange range = new TextRange(index, index + message.length());

        TextRange range1 = range.intersection(new TextRange(0, prefixEnd));
        TextRange range2 = range.intersection(new TextRange(suffixStart, totalLength));
        if (range1 != null && !range1.isEmpty()) {
          String message1 = range1.shiftLeft(index).substring(message);
          if (!range1.equals(range)) message1 += "..."; // add ellipsis to the last chunk before the cut
          messages.add(Pair.create(message1, type));
        }
        if (range2 != null && !range2.isEmpty()) {
          String message2 = range2.shiftLeft(index).substring(message);
          messages.add(Pair.create(message2, type));
        }
      }
      else {
        messages.add(Pair.create(message, type));
      }
      index += message.length();
    }
    showMessage(VcsConsoleLine.create(messages));
  }

  private void showMessage(@Nullable VcsConsoleLine line) {
    if (myProject.isDisposed()) return;
    ProjectLevelVcsManager.getInstance(myProject).addMessageToConsoleWindow(line);
  }
}
