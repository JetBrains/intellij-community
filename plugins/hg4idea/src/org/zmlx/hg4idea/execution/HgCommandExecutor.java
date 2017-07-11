/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.zmlx.hg4idea.execution;

import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.SystemProperties;
import com.intellij.vcsUtil.VcsImplUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.zmlx.hg4idea.HgGlobalSettings;
import org.zmlx.hg4idea.HgVcs;
import org.zmlx.hg4idea.HgVcsMessages;
import org.zmlx.hg4idea.util.HgEncodingUtil;
import org.zmlx.hg4idea.util.HgErrorUtil;
import org.zmlx.hg4idea.util.HgUtil;

import java.io.File;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

/**
 * <p>Executes an hg external command synchronously or asynchronously with the consequent call of {@link HgCommandResultHandler}</p>
 * <p/>
 * <p>Silence policy:
 * <li>if the command is silent, the fact of its execution will be recorded in the log, but not in the VCS console.
 * <li>if the command is not silent, which is default, it is written in the log and console.
 * <li>the command output is not written to the log or shown to console by default, but it can be changed via {@link #myShowOutput}
 * <li>error output is logged to the console and log, if the command is not silent.
 * </p>
 */

public class HgCommandExecutor {
  protected static final Logger LOG = Logger.getInstance(HgCommandExecutor.class.getName());

  // Other parts of the plugin count on the availability of the MQ extension, so make sure it is enabled
  private static final List<String> DEFAULT_OPTIONS = Arrays.asList("--config", "extensions.mq=", "--config", "ui.merge=internal:merge");

  protected final Project myProject;
  protected final HgVcs myVcs;
  protected final String myDestination;

  @NotNull private Charset myCharset;
  private boolean myIsSilent = false;
  private boolean myShowOutput = false;
  private boolean myIsBinary = false;

  private boolean myOutputAlwaysSuppressed = false;    //for command with enormous output, like log or cat

  public HgCommandExecutor(Project project) {
    this(project, null);
  }

  public HgCommandExecutor(Project project, @Nullable String destination) {
    myProject = project;
    myVcs = HgVcs.getInstance(project);
    myDestination = destination;
    myCharset = HgEncodingUtil.getDefaultCharset(myProject);
  }

  public void setCharset(@Nullable Charset charset) {
    if (charset != null) {
      myCharset = charset;
    }
  }

  public void setSilent(boolean isSilent) {
    myIsSilent = isSilent;
  }

  public void setShowOutput(boolean showOutput) {
    myShowOutput = showOutput;
  }

  public void setBinary(boolean isBinary) {
    myIsBinary = isBinary;
  }

  public void setOutputAlwaysSuppressed(boolean outputAlwaysSuppressed) {
    myOutputAlwaysSuppressed = outputAlwaysSuppressed;
  }

  public void execute(@Nullable final VirtualFile repo,
                      @NotNull final String operation,
                      @Nullable final List<String> arguments,
                      @Nullable final HgCommandResultHandler handler) {
    HgUtil.executeOnPooledThread(() -> {
      HgCommandResult result = executeInCurrentThread(repo, operation, arguments);
      if (handler != null) {
        handler.process(result);
      }
    }, myProject);
  }

  public HgCommandResult executeInCurrentThread(@Nullable final VirtualFile repo,
                                                @NotNull final String operation,
                                                @Nullable final List<String> arguments) {
    return executeInCurrentThread(repo, operation, arguments, false);
  }

  public HgCommandResult executeInCurrentThread(@Nullable final VirtualFile repo,
                                                @NotNull final String operation,
                                                @Nullable final List<String> arguments,
                                                boolean ignoreDefaultOptions) {
    HgCommandResult result = executeInCurrentThreadAndLog(repo, operation, arguments, ignoreDefaultOptions);
    if (HgErrorUtil.isUnknownEncodingError(result)) {
      setCharset(Charset.forName("utf8"));
      result = executeInCurrentThreadAndLog(repo, operation, arguments, ignoreDefaultOptions);
    }
    return result;
  }

  @Nullable
  private HgCommandResult executeInCurrentThreadAndLog(@Nullable final VirtualFile repo,
                                                       @NotNull final String operation,
                                                       @Nullable final List<String> arguments,
                                                       boolean ignoreDefaultOptions) {
    if (myProject == null || myProject.isDisposed() || myVcs == null) return null;

    ShellCommand shellCommand = createShellCommandWithArgs(repo, operation, arguments, ignoreDefaultOptions);
    try {
      long startTime = System.currentTimeMillis();
      LOG.debug(String.format("hg %s started", operation));
      HgCommandResult result = shellCommand.execute(myShowOutput, myIsBinary);
      LOG.debug(String.format("hg %s finished. Took %s ms", operation, System.currentTimeMillis() - startTime));
      logResult(result);
      return result;
    }
    catch (ShellCommandException e) {
      processError(e);
      return null;
    }
    catch (InterruptedException e) { // this may happen during project closing, no need to notify the user.
      LOG.info(e.getMessage(), e);
      return null;
    }
  }

  public void executeInCurrentThread(@Nullable VirtualFile repo,
                                     @NotNull String operation,
                                     @Nullable List<String> arguments,
                                     @NotNull HgLineProcessListener listener) {
    executeInCurrentThreadAndLog(repo, operation, arguments, listener);
    if (HgErrorUtil.isUnknownEncodingError(StringUtil.split(listener.getErrorOutput().toString(), SystemProperties.getLineSeparator()))) {
      setCharset(Charset.forName("utf8"));
      executeInCurrentThreadAndLog(repo, operation, arguments, listener);
    }
  }

  public void executeInCurrentThreadAndLog(@Nullable VirtualFile repo,
                                           @NotNull String operation,
                                           @Nullable List<String> arguments,
                                           @NotNull HgLineProcessListener listener) {
    if (myProject == null || myProject.isDisposed() || myVcs == null) return;

    ShellCommand shellCommand = createShellCommandWithArgs(repo, operation, arguments, false);
    try {
      long startTime = System.currentTimeMillis();
      LOG.debug(String.format("hg %s started", operation));
      shellCommand.execute(myShowOutput, listener);
      LOG.debug(String.format("hg %s finished. Took %s ms", operation, System.currentTimeMillis() - startTime));
    }
    catch (ShellCommandException e) {
      processError(e);
    }
    catch (InterruptedException e) { // this may happen during project closing, no need to notify the user.
      LOG.info(e.getMessage(), e);
    }
  }

  private void processError(@NotNull ShellCommandException e) {
    if (myVcs.getExecutableValidator().checkExecutableAndNotifyIfNeeded()) {
      // if the problem was not with invalid executable - show error.
      showError(e);
      LOG.info(e.getMessage(), e);
    }
  }

  @NotNull
  private ShellCommand createShellCommandWithArgs(@Nullable VirtualFile repo,
                                                  @NotNull String operation,
                                                  @Nullable List<String> arguments,
                                                  boolean ignoreDefaultOptions) {

    logCommand(operation, arguments);

    final List<String> cmdLine = new LinkedList<>();
    cmdLine.add(myVcs.getGlobalSettings().getHgExecutable());
    if (repo != null) {
      cmdLine.add("--repository");
      cmdLine.add(repo.getPath());
    }

    if (!ignoreDefaultOptions) {
      cmdLine.addAll(DEFAULT_OPTIONS);
    }
    cmdLine.add(operation);
    if (arguments != null && arguments.size() != 0) {
      cmdLine.addAll(arguments);
    }
    if (HgVcs.HGENCODING == null) {
      cmdLine.add("--encoding");
      cmdLine.add(HgEncodingUtil.getNameFor(myCharset));
    }

    String workingDir = repo != null ? repo.getPath() : null;
    return new ShellCommand(cmdLine, workingDir, myCharset);
  }

  // logging to the Version Control console (without extensions and configs)
  @SuppressWarnings("UseOfSystemOutOrSystemErr")
  protected void logCommand(@NotNull String operation, @Nullable List<String> arguments) {
    if (myProject.isDisposed()) {
      return;
    }
    final HgGlobalSettings settings = myVcs.getGlobalSettings();
    String exeName;
    final int lastSlashIndex = settings.getHgExecutable().lastIndexOf(File.separator);
    exeName = settings.getHgExecutable().substring(lastSlashIndex + 1);

    String str = String.format("%s %s %s", exeName, operation, arguments == null ? "" : StringUtil.escapeStringCharacters(StringUtil.join(arguments, " ")));
    //remove password from path before log
    final String cmdString = myDestination != null ? HgUtil.removePasswordIfNeeded(str) : str;
    // log command
    if (!myIsSilent) {
      LOG.info(cmdString);
      myVcs.showMessageInConsole(cmdString, ConsoleViewContentType.NORMAL_OUTPUT);
    }
    else {
      LOG.debug(cmdString);
    }
  }

  @SuppressWarnings("UseOfSystemOutOrSystemErr")
  private void logResult(@NotNull HgCommandResult result) {
    // log output if needed
    if (!result.getRawOutput().isEmpty()) {
      if (!myOutputAlwaysSuppressed) {
        if (!myIsSilent && myShowOutput) {
          LOG.info(result.getRawOutput());
          myVcs.showMessageInConsole(result.getRawOutput(), ConsoleViewContentType.SYSTEM_OUTPUT);
        }
        else {
          LOG.debug(result.getRawOutput());
        }
      }
    }

    // log error
    if (!result.getRawError().isEmpty()) {
      if (!myIsSilent) {
        LOG.info(result.getRawError());
        myVcs.showMessageInConsole(result.getRawError(), ConsoleViewContentType.ERROR_OUTPUT);
      }
      else {
        LOG.debug(result.getRawError());
      }
    }
  }

  protected void showError(Exception e) {
    final HgVcs vcs = HgVcs.getInstance(myProject);
    if (vcs == null) return;
    String message = HgVcsMessages.message("hg4idea.command.executable.error", vcs.getGlobalSettings().getHgExecutable()) +
                     "\nOriginal Error:\n" +
                     e.getMessage();
    VcsImplUtil.showErrorMessage(myProject, message, HgVcsMessages.message("hg4idea.error"));
  }
}
