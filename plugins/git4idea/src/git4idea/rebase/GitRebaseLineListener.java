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

import com.intellij.openapi.util.Key;
import git4idea.commands.GitLineHandlerAdapter;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This listener gathers information relevant to the progress of the rebase operation
 */
public class GitRebaseLineListener extends GitLineHandlerAdapter {
  private static final Pattern PROGRESS = Pattern.compile("^Rebasing \\((\\d+)/(\\d+)\\)$");
  private String myProgressLine;

  @Override
  public synchronized void onLineAvailable(String line, Key outputType) {
    if (PROGRESS.matcher(line).matches()) {
      myProgressLine = line;
    }
  }

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
    return new Result(total, current);
  }

  public static final class Result {
    /**
     * The total number of commits
     */
    public final int total;
    /**
     * The commit number that is being currently processed
     */
    public final int current;

    public Result(int total, int current) {
      this.total = total;
      this.current = current;
    }
  }
}
