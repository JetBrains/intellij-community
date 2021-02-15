// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.commands;

import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import git4idea.GitUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

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

  public GitCommandResult(boolean startFailed,
                          int exitCode,
                          @NotNull List<String> errorOutput,
                          @NotNull List<String> output) {
    this(startFailed, exitCode, false, errorOutput, output);
  }

  private GitCommandResult(boolean startFailed,
                           int exitCode,
                           boolean authenticationFailed,
                           @NotNull List<String> errorOutput,
                           @NotNull List<String> output) {
    myExitCode = exitCode;
    myStartFailed = startFailed;
    myAuthenticationFailed = authenticationFailed;
    myErrorOutput = errorOutput;
    myOutput = output;
  }

  /**
   * @return result with specified value for authentication failure
   */
  @NotNull
  static GitCommandResult withAuthentication(@NotNull GitCommandResult result, boolean authenticationFailed) {
    return new GitCommandResult(result.myStartFailed,
                                result.myExitCode,
                                authenticationFailed,
                                result.myErrorOutput,
                                result.myOutput);
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

  @NotNull
  public List<String> getOutput() {
    return Collections.unmodifiableList(myOutput);
  }

  public int getExitCode() {
    return myExitCode;
  }

  public boolean isAuthenticationFailed() {
    return myAuthenticationFailed;
  }

  @NotNull
  public List<String> getErrorOutput() {
    return Collections.unmodifiableList(myErrorOutput);
  }

  @NonNls
  @Override
  public String toString() {
    return String.format("{%d} %nOutput: %n%s %nError output: %n%s", myExitCode, myOutput, myErrorOutput);
  }

  @NotNull
  @NlsSafe
  public @NlsContexts.NotificationContent String getErrorOutputAsHtmlString() {
    return StringUtil.join(cleanup(getErrorOrStdOutput()), UIUtil.BR);
  }

  @NotNull
  @NlsSafe
  public String getErrorOutputAsJoinedString() {
    return StringUtil.join(cleanup(getErrorOrStdOutput()), "\n");
  }

  // in some cases operation fails but no explicit error messages are given, in this case return the output to display something to user
  @NotNull
  @NlsSafe
  private List<String> getErrorOrStdOutput() {
    return myErrorOutput.isEmpty() && !success() ? myOutput : myErrorOutput;
  }

  @NotNull
  @NlsSafe
  public String getOutputAsJoinedString() {
    return StringUtil.join(myOutput, "\n");
  }

  /**
   * Check if execution was successful and return textual result or throw exception
   *
   * @param ignoredErrorCodes list of non-zero exit codes the are considered success exit codes
   * @return result of {@link #getOutputAsJoinedString()}
   * @throws VcsException with message from {@link #getErrorOutputAsJoinedString()}
   */
  @NotNull
  @NlsSafe
  public String getOutputOrThrow(int... ignoredErrorCodes) throws VcsException {
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
    if (!success(ignoredErrorCodes)) throw new VcsException(getErrorOutputAsJoinedString());
  }

  @NotNull
  static GitCommandResult startError(@NotNull @Nls String error) {
    return new GitCommandResult(true, -1, Collections.singletonList(error), Collections.emptyList());
  }

  @NotNull
  public static GitCommandResult error(@NotNull @Nls String error) {
    return new GitCommandResult(false, 1, Collections.singletonList(error), Collections.emptyList());
  }

  public boolean cancelled() {
    return false; // will be implemented later
  }

  @NotNull
  private static Collection<String> cleanup(@NotNull Collection<String> errorOutput) {
    return ContainerUtil.map(errorOutput, errorMessage -> GitUtil.cleanupErrorPrefixes(errorMessage));
  }

  protected boolean hasStartFailed() {
    return myStartFailed;
  }
}
