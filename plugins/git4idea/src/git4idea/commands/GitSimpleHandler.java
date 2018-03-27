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
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.i18n.GitBundle;
import org.jetbrains.annotations.NotNull;

import java.io.File;

/**
 * Simple Git handler that accumulates stdout and stderr and has nothing on stdin.
 * The handler executes commands synchronously with cancellable progress indicator.
 * <p/>
 * The class also includes a number of static utility methods that represent some
 * simple commands.
 *
 * @deprecated use {@link Git} and {@link GitLineHandler}
 */
@Deprecated
public class GitSimpleHandler extends GitTextHandler {

  public static final String DURING_EXECUTING_ERROR_MESSAGE = "during executing";

  /**
   * Stderr output
   */
  private final StringBuilder myStderr = new StringBuilder();
  /**
   * Reminder of the last stderr line
   */
  private final StringBuilder myStderrLine = new StringBuilder();
  /**
   * Stdout output
   */
  private final StringBuilder myStdout = new StringBuilder();
  /**
   * Reminder of the last stdout line
   */
  private final StringBuilder myStdoutLine = new StringBuilder();

  /**
   * A constructor
   *
   * @param project   a project
   * @param directory a process directory
   * @param command   a command to execute
   */
  @SuppressWarnings({"WeakerAccess"})
  public GitSimpleHandler(@NotNull Project project, @NotNull File directory, @NotNull GitCommand command) {
    super(project, directory, command);
  }

  /**
   * A constructor
   *
   * @param project   a project
   * @param directory a process directory
   * @param command   a command to execute
   */
  @SuppressWarnings({"WeakerAccess"})
  public GitSimpleHandler(@NotNull final Project project, @NotNull final VirtualFile directory, @NotNull final GitCommand command) {
    super(project, directory, command);
  }

  /**
   * {@inheritDoc}
   */
  protected void processTerminated(final int exitCode) {
    String stdout = myStdoutLine.toString();
    String stderr = myStderrLine.toString();
    if (!isStdoutSuppressed() && !StringUtil.isEmptyOrSpaces(stdout)) {
      LOG.info(stdout.trim());
      myStdoutLine.setLength(0);
    }
    else if (!isStderrSuppressed() && !StringUtil.isEmptyOrSpaces(stderr)) {
      LOG.info(stderr.trim());
      myStderrLine.setLength(0);
    }
    else {
      LOG.debug(stderr.trim());
      OUTPUT_LOG.debug(stdout.trim());
    }
  }

  /**
   * For silent handlers, print out everything
   */
  @Deprecated
  public void unsilence() {
  }

  /**
   * {@inheritDoc}
   */
  protected void onTextAvailable(final String text, final Key outputType) {
    final StringBuilder entire;
    final StringBuilder lineRest;
    final boolean suppressed;
    if (ProcessOutputTypes.STDOUT == outputType) {
      entire = myStdout;
      lineRest = myStdoutLine;
      suppressed = isStdoutSuppressed();
    }
    else if (ProcessOutputTypes.STDERR == outputType) {
      entire = myStderr;
      lineRest = myStderrLine;
      suppressed = isStderrSuppressed();
    }
    else {
      return;
    }
    entire.append(text);
    if (suppressed && !LOG.isDebugEnabled()) {
      return;
    }
    int last = lineRest.length() > 0 ? lineRest.charAt(lineRest.length() - 1) : -1;
    int start = 0;
    for (int i = 0; i < text.length(); i++) {
      char ch = text.charAt(i);
      if (last == '\n' || last == '\r') {
        int savedPos;
        if ((ch == '\n' || ch == '\r') && ch != last) {
          savedPos = i - 1;
        }
        else {
          savedPos = i;
        }
        if (last != '\r' || savedPos != i) {
          String line;
          if (lineRest.length() == 0) {
            line = lineRest.append(text.substring(start, savedPos)).toString();
            lineRest.setLength(0);
          }
          else {
            line = text.substring(start, savedPos);
          }
          if (!StringUtil.isEmptyOrSpaces(line)) {
            if (!suppressed) {
              LOG.info(line.trim());
            }
            else {
              LOG.debug(line.trim());
            }
          }
        }
        start = savedPos;
      }
      last = ch;
    }
    if (start != text.length()) {
      lineRest.append(text.substring(start));
    }
  }

  /**
   * @return stderr contents
   */
  public String getStderr() {
    return myStderr.toString();
  }

  /**
   * @return stdout contents
   */
  public String getStdout() {
    return myStdout.toString();
  }

  /**
   * Execute without UI. If UI interactions are required (for example SSH popups or progress dialog), use {@link Git} methods.
   *
   * @return a value if process was successful
   * @throws VcsException exception if process failed to start.
   */
  public String run() throws VcsException {
    Ref<VcsException> exRef = Ref.create();
    Ref<String> resultRef = Ref.create();
    addListener(new GitHandlerListener() {
      public void processTerminated(final int exitCode) {
        try {
          if (exitCode == 0 || isIgnoredErrorCode(exitCode)) {
            resultRef.set(getStdout());
          }
          else {
            String msg = getStderr();
            if (msg.length() == 0) {
              msg = getStdout();
            }
            if (msg.length() == 0) {
              msg = GitBundle.message("git.error.exit", exitCode);
            }
            exRef.set(new VcsException(msg));
          }
        }
        catch (Throwable t) {
          exRef.set(new VcsException(t.toString(), t));
        }
      }

      public void startFailed(final Throwable exception) {
        exRef.set(
          new VcsException("Process failed to start (" + myCommandLine.getCommandLineString() + "): " + exception.toString(), exception));
      }
    });
    runInCurrentThread();
    if (!exRef.isNull()) {
      throw new VcsException(exRef.get().getMessage() + " " + DURING_EXECUTING_ERROR_MESSAGE + " " + printableCommandLine(), exRef.get());
    }
    if (resultRef.isNull()) {
      throw new VcsException("The git command returned null: " + printableCommandLine());
    }
    return resultRef.get();
  }
}
