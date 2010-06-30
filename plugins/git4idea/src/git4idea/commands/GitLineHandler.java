/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package git4idea.commands;

import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.EventDispatcher;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * The handler that is based on per-line processing of the text.
 */
public class GitLineHandler extends GitTextHandler {
  /**
   * the partial line from stdout stream
   */
  private final StringBuilder myStdoutLine = new StringBuilder();
  /**
   * the partial line from stderr stream
   */
  private final StringBuilder myStderrLine = new StringBuilder();
  /**
   * Line listeners
   */
  private final EventDispatcher<GitLineHandlerListener> myLineListeners = EventDispatcher.create(GitLineHandlerListener.class);

  /**
   * A constructor
   *
   * @param project   a project
   * @param directory a process directory
   * @param command   a command to execute
   */
  @SuppressWarnings({"WeakerAccess"})
  public GitLineHandler(@NotNull Project project, @NotNull File directory, @NotNull GitCommand command) {
    super(project, directory, command);
  }

  /**
   * A constructor
   *
   * @param project a project
   * @param vcsRoot a process directory
   * @param command a command to execute
   */
  public GitLineHandler(@NotNull final Project project, @NotNull final VirtualFile vcsRoot, @NotNull final GitCommand command) {
    super(project, vcsRoot, command);
  }

  /**
   * {@inheritDoc}
   */
  protected void processTerminated(final int exitCode) {
    // force newline
    if (!isStdoutSuppressed() && myStdoutLine.length() != 0) {
      onTextAvailable("\n\r", ProcessOutputTypes.STDOUT);
    }
    else if (!isStderrSuppressed() && myStderrLine.length() != 0) {
      onTextAvailable("\n\r", ProcessOutputTypes.STDERR);
    }
  }


  /**
   * Add listener
   *
   * @param listener a listener to add
   */
  public void addLineListener(GitLineHandlerListener listener) {
    super.addListener(listener);
    myLineListeners.addListener(listener);
  }

  /**
   * {@inheritDoc}
   */
  protected void onTextAvailable(final String text, final Key outputType) {
    Iterator<String> lines = splitText(text).iterator();
    if (ProcessOutputTypes.STDOUT == outputType) {
      notifyLines(outputType, lines, myStdoutLine);
    }
    else if (ProcessOutputTypes.STDERR == outputType) {
      notifyLines(outputType, lines, myStderrLine);
    }
  }

  /**
   * Notify listeners for each complete line. Note that in the case of stderr, the last line is saved.
   *
   * @param outputType  output type
   * @param lines       line iterator
   * @param lineBuilder a line builder
   */
  private void notifyLines(final Key outputType, final Iterator<String> lines, final StringBuilder lineBuilder) {
    if (!lines.hasNext()) return;
    if (lineBuilder.length() > 0) {
      lineBuilder.append(lines.next());
      if (lines.hasNext()) {
        // line is complete
        final String line = lineBuilder.toString();
        notifyLine(line, outputType);
        lineBuilder.setLength(0);
      }
    }
    while (true) {
      String line = lines.next();
      if (lines.hasNext()) {
        notifyLine(line, outputType);
      }
      else {
        if (line.length() > 0) {
          lineBuilder.append(line);
        }
        break;
      }
    }
  }

  /**
   * Notify single line
   *
   * @param line       a line to notify
   * @param outputType output type
   */
  private void notifyLine(final String line, final Key outputType) {
    String trimmed = trimLineSeparator(line);
    // if line ends with return, then it is a progress line, ignore it
    if (myVcs != null && !"\r".equals(line.substring(trimmed.length()))) {
      if (outputType == ProcessOutputTypes.STDOUT && !isStdoutSuppressed()) {
        myVcs.showMessages(trimmed);
      }
      else if (outputType == ProcessOutputTypes.STDERR && !isStderrSuppressed()) {
        myVcs.showErrorMessages(trimmed);
      }
    }
    myLineListeners.getMulticaster().onLineAvailable(trimmed, outputType);
  }

  /**
   * Trim line separator from new line if it presents
   *
   * @param line a line to process
   * @return a trimmed line
   */
  private static String trimLineSeparator(String line) {
    int n = line.length();
    if (n == 0) {
      return line;
    }
    char ch = line.charAt(n - 1);
    if (ch == '\n' || ch == '\r') {
      n--;
    }
    else {
      return line;
    }
    if (n > 0) {
      char ch2 = line.charAt(n - 1);
      if ((ch2 == '\n' || ch2 == '\r') && ch2 != ch) {
        n--;
      }
    }
    return line.substring(0, n);

  }

  /**
   * Split text into lines. New line characters are treated as separators. So if the text starts
   * with newline, empty string will be the first element, if the text ends with new line, the
   * empty string will be the last element. The returned lines will be substrings of
   * the text argument. The new line characters are included into the line text.
   *
   * @param text a text to split
   * @return a list of elements (note that there are always at least one element)
   */
  private static List<String> splitText(String text) {
    int startLine = 0;
    int i = 0;
    int n = text.length();
    ArrayList<String> rc = new ArrayList<String>();
    while (i < n) {
      switch (text.charAt(i)) {
        case '\n':
          i++;
          if (i < n && text.charAt(i) == '\r') {
            i++;
          }
          rc.add(text.substring(startLine, i));
          startLine = i;
          break;
        case '\r':
          i++;
          if (i < n && text.charAt(i) == '\n') {
            i++;
          }
          rc.add(text.substring(startLine, i));
          startLine = i;
          break;
        default:
          i++;
      }
    }
    rc.add(text.substring(startLine, i));
    return rc;
  }
}
