// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.commands;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessListener;
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
  public GitSimpleHandler(final @NotNull Project project, final @NotNull VirtualFile directory, final @NotNull GitCommand command) {
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
    int last = !lineRest.isEmpty() ? lineRest.charAt(lineRest.length() - 1) : -1;
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
          if (lineRest.isEmpty()) {
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
  public @NlsSafe String getStderr() {
    return myStderr.toString();
  }

  /**
   * @return stdout contents
   */
  public @NlsSafe String getStdout() {
    return myStdout.toString();
  }

  /**
   * Execute without UI. If UI interactions are required (for example SSH popups or progress dialog), use {@link Git} methods.
   *
   * @return a value if process was successful
   * @throws VcsException exception if process failed to start.
   */
  public @NlsSafe String run() throws VcsException {
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
            if (msg.isEmpty()) {
              msg = getStdout();
            }
            if (msg.isEmpty()) {
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
      public void startFailed(final @NotNull Throwable exception) {
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

  @Override
  protected OSProcessHandler createProcess(@NotNull GeneralCommandLine commandLine) throws ExecutionException {
    OSProcessHandler process = super.createProcess(commandLine);
    process.addProcessListener(new ProcessListener() {
      @Override
      public void onTextAvailable(@NotNull ProcessEvent event,
                                  @NotNull Key outputType) {
        GitSimpleHandler.this.onTextAvailable(event.getText(), outputType);
      }
    });
    return process;
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
