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

import org.jetbrains.annotations.NotNull;

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
  
  public GitCommandResult(boolean success, int exitCode, @NotNull List<String> errorOutput) {
    myExitCode = exitCode;
    mySuccess = success;
    myErrorOutput = errorOutput;
  }

  /**
   * @return we think that the operation succeeded
   */
  public boolean success() {
    return mySuccess;
  }

  /**
   * @return the part of output that we treated as an error.
   */
  @NotNull
  public List<String> getErrorOutput() {
    return myErrorOutput;
  }
}
