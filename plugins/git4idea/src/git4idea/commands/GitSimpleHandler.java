// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.commands;

import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.i18n.GitBundle;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;

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
   * Error codes that are ignored for the handler
   */
  private final HashSet<Integer> myIgnoredErrorCodes = new HashSet<>();

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
  @Override
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
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
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
            line = lineRest.append(text, start, savedPos).toString();
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
  @NlsSafe
  public String getStderr() {
    return myStderr.toString();
  }

  /**
   * @return stdout contents
   */
  @NlsSafe
  public String getStdout() {
    return myStdout.toString();
  }

  /**
   * Execute without UI. If UI interactions are required (for example SSH popups or progress dialog), use {@link Git} methods.
   *
   * @return a value if process was successful
   * @throws VcsException exception if process failed to start.
   */
  @NlsSafe
  public String run() throws VcsException {
    Ref<VcsException> exRef = Ref.create();
    Ref<String> resultRef = Ref.create();
    addListener(new GitHandlerListener() {
      @Override
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

      @Override
      public void startFailed(@NotNull final Throwable exception) {
        exRef.set(new VcsException(GitBundle.message("git.executable.unknown.error.message", exception.getMessage()), exception));
      }
    });
    try {
      runInCurrentThread();
    }
    catch (IOException e) {
      exRef.set(new VcsException(e.getMessage(), e));
    }
    if (!exRef.isNull()) {
      throw exRef.get();
    }
    if (resultRef.isNull()) {
      throw new VcsException(GitBundle.message("git.error.cant.process.output", printableCommandLine()));
    }
    return resultRef.get();
  }

  /**
   * Add error code to ignored list
   *
   * @param code the code to ignore
   */
  public void ignoreErrorCode(int code) {
    myIgnoredErrorCodes.add(code);
  }

  /**
   * Check if error code should be ignored
   *
   * @param code a code to check
   * @return true if error code is ignorable
   */
  public boolean isIgnoredErrorCode(int code) {
    return myIgnoredErrorCodes.contains(code);
  }
}
