/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package git4idea.history;

import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.util.Consumer;
import git4idea.GitFormatException;
import git4idea.commands.GitLineHandler;
import git4idea.commands.GitLineHandlerListener;
import git4idea.i18n.GitBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * This class collects output of git log command. It separates the output into parts that correspond to commit records,
 * and feeds them to the consumer. It does not store output in order to save memory.
 */
class GitLogOutputSplitter implements GitLineHandlerListener {
  @NotNull private final GitLineHandler myHandler;
  @NotNull private final GitLogParser myParser;
  @NotNull private final Consumer<GitLogRecord> myRecordConsumer;

  @NotNull private final StringBuilder myErrors = new StringBuilder();
  @Nullable private VcsException myException = null;

  public GitLogOutputSplitter(@NotNull GitLineHandler handler,
                              @NotNull GitLogParser parser,
                              @NotNull Consumer<GitLogRecord> recordConsumer) {
    myHandler = handler;
    myParser = parser;
    myRecordConsumer = recordConsumer;

    myHandler.addLineListener(this);
  }

  @Override
  public void onLineAvailable(String line, Key outputType) {
    if (outputType == ProcessOutputTypes.STDERR) {
      myErrors.append(line).append("\n");
    }
    else if (outputType == ProcessOutputTypes.STDOUT) {
      try {
        processOutputLine(line);
      }
      catch (Exception e) {
        myException = new VcsException(e);
      }
    }
  }

  private void processOutputLine(@NotNull String line) {
    try {
      GitLogRecord record = myParser.parseLine(line);
      if (record != null) {
        record.setUsedHandler(myHandler);
        myRecordConsumer.consume(record);
      }
    }
    catch (ProcessCanceledException pce) {
      throw pce;
    }
    catch (GitFormatException e) {
      myParser.clear();
      myException = new VcsException("Error while parsing line " + line, e);
    }
    catch (Throwable t) {
      myException = new VcsException(t);
    }
  }

  @Override
  public void processTerminated(int exitCode) {
    if (exitCode != 0) {
      String errorMessage = myErrors.toString();
      if (errorMessage.isEmpty()) {
        errorMessage = GitBundle.message("git.error.exit", exitCode);
      }
      myException = new VcsException(errorMessage + "\nCommand line: [" + myHandler.printableCommandLine() + "]");
    }
    else {
      try {
        GitLogRecord record = myParser.finish();
        if (record != null) {
          record.setUsedHandler(myHandler);
          myRecordConsumer.consume(record);
        }
      }
      catch (Throwable t) {
        myException = new VcsException(t);
      }
    }
  }

  @Override
  public void startFailed(Throwable exception) {
    myException = new VcsException(exception);
  }

  public boolean hasErrors() {
    return myException != null;
  }

  public void reportErrors() throws VcsException {
    if (myException != null) {
      if (myException.getCause() instanceof ProcessCanceledException) {
        throw (ProcessCanceledException)myException.getCause();
      }
      throw myException;
    }
  }
}
