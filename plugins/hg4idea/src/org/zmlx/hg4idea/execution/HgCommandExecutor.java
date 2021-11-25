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
import com.intellij.ide.impl.TrustedProjects;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcsUtil.VcsImplUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.zmlx.hg4idea.HgBundle;
import org.zmlx.hg4idea.HgExecutableManager;
import org.zmlx.hg4idea.HgVcs;
import org.zmlx.hg4idea.util.HgEncodingUtil;
import org.zmlx.hg4idea.util.HgErrorUtil;
import org.zmlx.hg4idea.util.HgUtil;

import java.io.File;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
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

  @Nullable
  public HgCommandResult executeInCurrentThread(@Nullable final VirtualFile repo,
                                                @NotNull final @NonNls String operation,
                                                @Nullable final List<@NonNls String> arguments) {
    return executeInCurrentThread(repo, operation, arguments, false);
  }

  @Nullable
  public HgCommandResult executeInCurrentThread(@Nullable VirtualFile repo,
                                                @NotNull @NonNls String operation,
                                                @Nullable List<@NonNls String> arguments,
                                                boolean ignoreDefaultOptions) {
    ShellCommand.CommandResultCollector collector = new ShellCommand.CommandResultCollector(myIsBinary);
    boolean success = executeInCurrentThread(repo, operation, arguments, ignoreDefaultOptions, collector);
    return success ? collector.getResult() : null;
  }

  public boolean executeInCurrentThread(@Nullable VirtualFile repo,
                                        @NotNull @NonNls String operation,
                                        @Nullable List<@NonNls String> arguments,
                                        boolean ignoreDefaultOptions,
                                        @NotNull HgLineProcessListener listener) {
    boolean success = executeInCurrentThreadAndLog(repo, operation, arguments, ignoreDefaultOptions, listener);
    List<String> errors = StringUtil.split(listener.getErrorOutput().toString(), System.lineSeparator());
    if (success && HgErrorUtil.isUnknownEncodingError(errors)) {
      setCharset(StandardCharsets.UTF_8);
      return executeInCurrentThreadAndLog(repo, operation, arguments, ignoreDefaultOptions, listener);
    }
    return success;
  }

  private boolean executeInCurrentThreadAndLog(@Nullable VirtualFile repo,
                                               @NotNull @NonNls String operation,
                                               @Nullable List<@NonNls String> arguments,
                                               boolean ignoreDefaultOptions,
                                               @NotNull HgLineProcessListener listener) {
    if (myProject == null || myProject.isDisposed() || myVcs == null) return false;
    if (!myProject.isDefault() && !TrustedProjects.isTrusted(myProject)) {
      throw new IllegalStateException("Shouldn't be possible to run a Hg command in the safe mode");
    }

    ShellCommand shellCommand = createShellCommandWithArgs(repo, operation, arguments, ignoreDefaultOptions);
    try {
      long startTime = System.currentTimeMillis();
      LOG.debug(String.format("hg %s started", operation));
      shellCommand.execute(myShowOutput, myIsBinary, listener);
      LOG.debug(String.format("hg %s finished. Took %s ms", operation, System.currentTimeMillis() - startTime));
      return true;
    }
    catch (ShellCommandException e) {
      processError(e);
      return false;
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
                                                  @NotNull @NonNls String operation,
                                                  @Nullable List<@NonNls String> arguments,
                                                  boolean ignoreDefaultOptions) {

    logCommand(operation, arguments);

    final List<String> cmdLine = new LinkedList<>();
    cmdLine.add(HgExecutableManager.getInstance().getHgExecutable(myProject));
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
  protected void logCommand(@NotNull String operation, @Nullable List<String> arguments) {
    if (myProject.isDisposed()) {
      return;
    }
    String exeName = HgExecutableManager.getInstance().getHgExecutable(myProject);
    final int lastSlashIndex = exeName.lastIndexOf(File.separator);
    exeName = exeName.substring(lastSlashIndex + 1);

    @NonNls String str = String.format("%s %s %s", exeName, operation,
                                       arguments == null ? "" : StringUtil.escapeStringCharacters(StringUtil.join(arguments, " ")));
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

  protected void showError(Exception e) {
    final HgVcs vcs = HgVcs.getInstance(myProject);
    if (vcs == null) return;
    String message = HgBundle.message("hg4idea.command.executor.error", HgExecutableManager.getInstance().getHgExecutable(myProject),
                                      e.getMessage());
    VcsImplUtil.showErrorMessage(myProject, message, HgBundle.message("hg4idea.error"));
  }
}
