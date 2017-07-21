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
  private static final int OUTPUT_CAPACITY_THRESHOLD = 5_000_000;
  @NotNull private final GitLineHandler myHandler;
  @NotNull private final Consumer<StringBuilder> myRecordConsumer;

  @NotNull private final StringBuilder myOutput = new StringBuilder();
  @NotNull private final StringBuilder myErrors = new StringBuilder();
  @Nullable private VcsException myException = null;

  private boolean myIsInsideBody = true;

  public GitLogOutputSplitter(@NotNull GitLineHandler handler,
                              @NotNull Consumer<StringBuilder> recordConsumer) {
    myHandler = handler;
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
    // format of the record is <RECORD_START><BODY><RECORD_END><CHANGES>
    // then next record goes
    // (rather inconveniently, after RECORD_END there is a list of modified files)
    // so here I'm trying to find text between two RECORD_START symbols
    // that simultaneously contains a RECORD_END
    // this helps to deal with commits like a929478f6720ac15d949117188cd6798b4a9c286 in linux repo that have RECORD_START symbols in the message
    // wont help with RECORD_END symbols in the message however (have not seen those yet)

    if (myIsInsideBody) {
      // find body
      int bodyEnd = line.indexOf(GitLogParser.RECORD_END);
      if (bodyEnd >= 0) {
        myIsInsideBody = false;
        myOutput.append(line.substring(0, bodyEnd + GitLogParser.RECORD_END.length()));
        processOutputLine(line.substring(bodyEnd + GitLogParser.RECORD_END.length()));
      }
      else {
        myOutput.append(line).append("\n");
      }
    }
    else {
      int nextRecordStart = line.indexOf(GitLogParser.RECORD_START);
      if (nextRecordStart >= 0) {
        myOutput.append(line.substring(0, nextRecordStart));
        myRecordConsumer.consume(myOutput);
        myOutput.setLength(0);
        if (myOutput.capacity() >= OUTPUT_CAPACITY_THRESHOLD) myOutput.trimToSize();
        myIsInsideBody = true;
        processOutputLine(line.substring(nextRecordStart));
      }
      else {
        myOutput.append(line).append("\n");
      }
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
        myRecordConsumer.consume(myOutput);
      }
      catch (Exception e) {
        myException = new VcsException(e);
      }
    }
  }

  @Override
  public void startFailed(Throwable exception) {
    myException = new VcsException(exception);
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
