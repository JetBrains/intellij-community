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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * This class represents the result of a Git command execution.
 *
 * @author Kirill Likhodedov
 */
public class GitCommandResult {

  private final boolean mySuccess;
  private final int myExitCode;               // non-zero exit code doesn't necessarily mean an error
  private final List<String> myErrorOutput;
  private final List<String> myOutput;
  @Nullable private final Throwable myException;

  public GitCommandResult(boolean success, int exitCode, @NotNull List<String> errorOutput, @NotNull List<String> output,
                          @Nullable Throwable exception) {
    myExitCode = exitCode;
    mySuccess = success;
    myErrorOutput = errorOutput;
    myOutput = output;
    myException = exception;
  }

  /**
   * @return we think that the operation succeeded
   */
  public boolean success() {
    return mySuccess;
  }

  @NotNull
  public List<String> getOutput() {
    return new ArrayList<String>(myOutput);
  }

  @Override
  public String toString() {
    return String.format("{%d} %nOutput: %n%s %nError output: %n%s", myExitCode, myOutput, myErrorOutput);
  }

  @NotNull
  public String getErrorOutputAsHtmlString() {
    return StringUtil.join(myErrorOutput, "<br/>");
  }
  
  public String getErrorOutputAsJoinedString() {
    return StringUtil.join(myErrorOutput, "\n");
  }

  @NotNull
  public String getOutputAsJoinedString() {
    return StringUtil.join(myOutput, "\n");
  }

  @Nullable
  public Throwable getException() {
    return myException;
  }

  @NotNull
  public static GitCommandResult error(@NotNull String error) {
    return new GitCommandResult(false, 1, Collections.singletonList(error), Collections.<String>emptyList(), null);
  }
}
