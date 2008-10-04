/*
 * Copyright 2000-2008 JetBrains s.r.o.
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
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.File;

/**
 * Simple Git hanlder that accumulates stdout and stderr and has nothing on stdin.
 * The handler executes commands sychronously with cancellable progress indicator.
 * <p/>
 * The class also includes a number of static utility methods that represent some
 * simple commands.
 */
public class GitSimpleHandler extends GitHandler {
  /**
   * Stderr output
   */
  private final StringBuilder myStderr = new StringBuilder();
  /**
   * Stdout output
   */
  private final StringBuilder myStdout = new StringBuilder();

  /**
   * A constructor
   *
   * @param project   a project
   * @param directory a process directory
   * @param command   a command to execute
   */
  public GitSimpleHandler(@NotNull Project project, @NotNull File directory, @NotNull @NonNls String command) {
    super(project, directory, command);
  }

  /**
   * {@inheritDoc}
   */
  protected void onTextAvailable(final String text, final Key outputType) {
    if (ProcessOutputTypes.STDOUT == outputType) {
      myStdout.append(text);
    }
    else if (ProcessOutputTypes.STDERR == outputType) {
      myStderr.append(text);
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
   * Prepare check repository handler. To do this ls-remote command is executed and attempts to match
   * master tag. This will likely return only single entry or none, if there is no master
   * branch. Stdout output is ignored. Stderr is used to construct exception message and shown
   * in error message box if exit is negative.
   *
   * @param project the project
   * @param url     the url to check
   */
  public static GitSimpleHandler checkRepository(Project project, final String url) {
    GitSimpleHandler handler = new GitSimpleHandler(project, new File("."), "ls-remote");
    handler.addParameters(url, "master");
    return handler;
  }
}
