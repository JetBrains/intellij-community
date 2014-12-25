/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package git4idea.rebase;

import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.openapi.util.Key;
import git4idea.commands.GitLineHandlerAdapter;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This listener gathers information relevant to the progress of the rebase operation
 */
public class GitRebaseLineListener extends GitLineHandlerAdapter {
  /**
   * Git rebase progress message
   */
  private static final Pattern PROGRESS = Pattern.compile("^Rebasing \\((\\d+)/(\\d+)\\)$");
  /**
   * The status
   */
  private Status myStatus;
  /**
   * The progress line
   */
  private String myProgressLine;

  /**
   * {@inheritDoc}
   */
  @Override
  public synchronized void onLineAvailable(String line, Key outputType) {
    if (outputType == ProcessOutputTypes.STDOUT) {
      if (PROGRESS.matcher(line).matches()) {
        myProgressLine = line;
        // a bit dodgy line since STDERR line could arrive before STDOUT line,
        // but in practice STDOUT comes first.
        myStatus = null;
      }
    }
    else {
      if (line.startsWith("You can amend the commit now")) {
        assert myStatus == null;
        myStatus = Status.EDIT;
      }
      else if (line.startsWith("Successfully rebased and updated")) {
        assert myStatus == null;
        myStatus = Status.FINISHED;
      }
      else if (line.startsWith("Automatic cherry-pick failed") || line.startsWith("When you have resolved this problem")) {
        assert myStatus == null || myStatus == Status.ERROR;
        myStatus = Status.CONFLICT;
      }
      else if (line.startsWith("Could not execute editor")) {
        assert myStatus == null;
        myStatus = myProgressLine == null ? Status.CANCELLED : Status.ERROR;
      }
      else if (line.startsWith("fatal") || line.startsWith("error: ") || line.startsWith("Cannot")) {
        if (myStatus != Status.CONFLICT) {
          myStatus = Status.ERROR;
        }
      }
    }
  }

  /**
   * @return the progress values as a pair
   */
  public synchronized Result getResult() {
    int total;
    int current;
    if (myProgressLine != null) {
      // the integers already matched the digits pattern, so they should parse
      final Matcher matcher = PROGRESS.matcher(myProgressLine);
      if (matcher.matches()) {
        current = Integer.parseInt(matcher.group(1));
        total = Integer.parseInt(matcher.group(2));
      }
      else {
        throw new IllegalStateException("The wrong current result line: " + myProgressLine);
      }
    }
    else {
      total = current = 0;
    }
    return new Result(myStatus == null ? Status.FINISHED : myStatus, total, current);
  }

  /**
   * The result of operation
   */
  public static final class Result {
    /**
     * The operation status
     */
    public final Status status;
    /**
     * The total number of commits
     */
    public final int total;
    /**
     * The commit number that is being currently processed
     */
    public final int current;

    /**
     * A constructor
     *
     * @param status  the status
     * @param total   the commit count
     * @param current the current commit
     */
    public Result(Status status, int total, int current) {
      this.status = status;
      this.total = total;
      this.current = current;
    }
  }

  /**
   * The current rebase status
   */
  public enum Status {
    /**
     * Rebase operation is cancelled (could not execute editor)
     */
    CANCELLED,
    /**
     * Rebase is finished
     */
    FINISHED,
    /**
     * Suspended for edit
     */
    EDIT,
    /**
     * Suspended due to the conflict
     */
    CONFLICT,
    /**
     * Suspended due to the error
     */
    ERROR
  }
}
