/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.util.containers.ContainerUtil;
import git4idea.GitUtil;
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

  @Override
  public String toString() {
    return String.format("{%d} %nOutput: %n%s %nError output: %n%s", myExitCode, myOutput, myErrorOutput);
  }

  @NotNull
  public String getErrorOutputAsHtmlString() {
    return StringUtil.join(cleanup(getErrorOrStdOutput()), "<br/>");
  }

  @NotNull
  public String getErrorOutputAsJoinedString() {
    return StringUtil.join(cleanup(getErrorOrStdOutput()), "\n");
  }

  // in some cases operation fails but no explicit error messages are given, in this case return the output to display something to user
  @NotNull
  private List<String> getErrorOrStdOutput() {
    return myErrorOutput.isEmpty() && !success() ? myOutput : myErrorOutput;
  }

  @NotNull
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
  public String getOutputOrThrow(int... ignoredErrorCodes) throws VcsException {
    if (!success(ignoredErrorCodes)) throw new VcsException(getErrorOutputAsJoinedString());
    return getOutputAsJoinedString();
  }

  /**
   * @return null
   * @deprecated use {@link #getErrorOutput()}
   */
  @Deprecated
  @Nullable
  public Throwable getException() {
    return null;
  }

  @NotNull
  static GitCommandResult startError(@NotNull String error) {
    return new GitCommandResult(true, -1, Collections.singletonList(error), Collections.emptyList());
  }

  @NotNull
  public static GitCommandResult error(@NotNull String error) {
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
