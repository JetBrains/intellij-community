/*
 * Copyright 2000-2008 JetBrains s.r.o.
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
import com.intellij.util.EventDispatcher;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * The handler that is based on per-line processing of the text.
 */
public class GitLineHandler extends GitHandler {
  /**
   * the partial line from stdout stream
   */
  final StringBuilder myStdoutLine = new StringBuilder();
  /**
   * the partial line from stderr stream
   */
  final StringBuilder myStderrLine = new StringBuilder();
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
  public GitLineHandler(@NotNull Project project, @NotNull File directory, @NonNls @NotNull String command) {
    super(project, directory, command);
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
   * @param lines       line interator
   * @param lineBuilder a line builder
   */
  private void notifyLines(final Key outputType, final Iterator<String> lines, final StringBuilder lineBuilder) {
    if (lineBuilder.length() > 0) {
      lineBuilder.append(lines.next());
      if (lines.hasNext()) {
        // line is complete
        final String line = lineBuilder.toString();
        myLineListeners.getMulticaster().onLineAvaiable(line, outputType);
        lineBuilder.setLength(0);
      }
    }
    while (true) {
      String line = lines.next();
      if (lines.hasNext()) {
        myLineListeners.getMulticaster().onLineAvaiable(line, outputType);
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
   * Split text into lines. New line characters are treated as separators. So if the text starts
   * with newline, empty string will be the first element, if the text ends with new line, the
   * empty string will be the last element. The returned lines will be substrings of
   * the text argument.
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
          rc.add(text.substring(startLine, i));
          i++;
          if (i < n && text.charAt(i) == '\r') {
            i++;
          }
          startLine = i;
          break;
        case '\r':
          rc.add(text.substring(startLine, i));
          i++;
          if (i < n && text.charAt(i) == '\n') {
            i++;
          }
          startLine = i;
          break;
        default:
          i++;
      }
    }
    rc.add(text.substring(startLine, i));
    return rc;
  }


  /**
   * Prepare clone handler
   *
   * @param project    a project
   * @param url        an url
   * @param directory  a base directory
   * @param name       a name to checkout
   * @param originName origin name (ignored if null or empty string)
   * @return a handler for clone operation
   */
  public static GitLineHandler clone(Project project, final String url, final File directory, final String name, final String originName) {
    GitLineHandler handler = new GitLineHandler(project, directory, "clone");
    if (originName != null && originName.length() > 0) {
      handler.addParameters("-o", originName);
    }
    handler.addParameters(url, name);
    return handler;
  }
}
