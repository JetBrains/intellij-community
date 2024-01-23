// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.commands;

import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import com.intellij.xml.util.XmlStringUtil;
import git4idea.GitUtil;
import git4idea.i18n.GitBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * This class represents the result of a Git command execution.
 *
 * @author Kirill Likhodedov
 */
public class GitCommandResult {

  private final boolean myStartFailed;
  private final int myExitCode;               // non-zero exit code doesn't necessarily mean an error
  private final boolean myAuthenticationFailed;
  private final List<String> myErrorOutput;
  private final List<String> myOutput;
  protected final @Nullable @Nls String myRootName;

  public GitCommandResult(boolean startFailed,
                          int exitCode,
                          @NotNull List<String> errorOutput,
                          @NotNull List<String> output) {
    this(startFailed, exitCode, errorOutput, output, null);
  }

  public GitCommandResult(boolean startFailed,
                          int exitCode,
                          @NotNull List<String> errorOutput,
                          @NotNull List<String> output,
                          @Nullable @Nls String rootName) {
    this(startFailed, exitCode, false, errorOutput, output, rootName);
  }

  private GitCommandResult(boolean startFailed,
                           int exitCode,
                           boolean authenticationFailed,
                           @NotNull List<String> errorOutput,
                           @NotNull List<String> output,
                           @Nullable @Nls String rootName) {
    myExitCode = exitCode;
    myStartFailed = startFailed;
    myAuthenticationFailed = authenticationFailed;
    myErrorOutput = errorOutput;
    myOutput = output;
    myRootName = rootName;
  }

  /**
   * @return result with specified value for authentication failure
   */
  static @NotNull GitCommandResult withAuthentication(@NotNull GitCommandResult result, boolean authenticationFailed) {
    return new GitCommandResult(result.myStartFailed,
                                result.myExitCode,
                                authenticationFailed,
                                result.myErrorOutput,
                                result.myOutput,
                                result.myRootName);
  }

  /**
   * To retain binary compatibility
   *
   * @return we think that the operation succeeded
   */
  public boolean success() {
    return success(new int[]{});
  }

  /**
   * @param ignoredErrorCodes list of non-zero exit codes the are considered success exit codes
   * @return we think that the operation succeeded
   */
  public boolean success(int... ignoredErrorCodes) {
    return !myStartFailed && (Arrays.stream(ignoredErrorCodes).anyMatch(i -> i == myExitCode) || myExitCode == 0);
  }

  /**
   * NOTE: The returned lines will have line separators trimmed.
   * This means that CRLF, LF and CR lines will not be distinguishable and "\r\r" will be reported as 3 empty lines.
   *
   * @see BufferingTextSplitter
   */
  public @NotNull List<String> getOutput() {
    return Collections.unmodifiableList(myOutput);
  }

  public int getExitCode() {
    return myExitCode;
  }

  public boolean isAuthenticationFailed() {
    return myAuthenticationFailed;
  }

  public @NotNull List<String> getErrorOutput() {
    return Collections.unmodifiableList(myErrorOutput);
  }

  @Override
  public @NonNls String toString() {
    return String.format("{%d} %nOutput: %n%s %nError output: %n%s", myExitCode, myOutput, myErrorOutput);
  }

  public @NotNull @NlsSafe @NlsContexts.NotificationContent String getErrorOutputAsHtmlString() {
    return StringUtil.join(cleanup(getErrorOrStdOutput()), XmlStringUtil::escapeString, UIUtil.BR);
  }

  public @NotNull @NlsSafe String getErrorOutputAsJoinedString() {
    return StringUtil.join(cleanup(getErrorOrStdOutput()), "\n");
  }

  // in some cases operation fails but no explicit error messages are given, in this case return the output to display something to user
  private @NotNull @NlsSafe List<String> getErrorOrStdOutput() {
    if (!myErrorOutput.isEmpty()) return myErrorOutput;
    if (success()) return Collections.emptyList();
    if (!myOutput.isEmpty()) return myOutput;
    return Collections.singletonList(GitBundle.message("git.error.exit", myExitCode));
  }

  /**
   * NOTE: The returned string will have its line separators converted to "\n".
   * This means that "\r\r" output from git will be reported as "\n\n" instead.
   *
   * @see BufferingTextSplitter
   */
  public @NotNull @NlsSafe String getOutputAsJoinedString() {
    return StringUtil.join(myOutput, "\n");
  }

  /**
   * Check if execution was successful and return textual result or throw exception
   *
   * @param ignoredErrorCodes list of non-zero exit codes the are considered success exit codes
   * @return result of {@link #getOutputAsJoinedString()}
   * @throws VcsException with message from {@link #getErrorOutputAsJoinedString()}
   */
  public @NotNull @NlsSafe String getOutputOrThrow(int... ignoredErrorCodes) throws VcsException {
    throwOnError(ignoredErrorCodes);
    return getOutputAsJoinedString();
  }

  /**
   * Check if execution was successful and do nothing or throw exception
   *
   * @param ignoredErrorCodes list of non-zero exit codes the are considered success exit codes
   * @throws VcsException with message from {@link #getErrorOutputAsJoinedString()}
   */
  public void throwOnError(int... ignoredErrorCodes) throws VcsException {
    if (success(ignoredErrorCodes)) return;
    String errorMessage = getErrorOutputAsJoinedString();
    if (myRootName != null) {
      errorMessage = "[" + myRootName + "] " + errorMessage;
    }
    throw new VcsException(errorMessage);
  }

  static @NotNull GitCommandResult startError(@NotNull @Nls String error) {
    return new GitCommandResult(true, -1, Collections.singletonList(error), Collections.emptyList(), null);
  }

  public static @NotNull GitCommandResult error(@NotNull @Nls String error) {
    return new GitCommandResult(false, 1, Collections.singletonList(error), Collections.emptyList(), null);
  }

  /**
   * @deprecated {@link GitHandler} throws {@link com.intellij.openapi.progress.ProcessCanceledException} instead of returning this state.
   */
  @Deprecated
  public boolean cancelled() {
    return false;
  }

  private static @NotNull Collection<String> cleanup(@NotNull Collection<String> errorOutput) {
    return ContainerUtil.map(errorOutput, errorMessage -> GitUtil.cleanupErrorPrefixes(errorMessage));
  }

  protected boolean hasStartFailed() {
    return myStartFailed;
  }
}
