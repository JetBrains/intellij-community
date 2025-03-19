// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.history;

import com.intellij.execution.process.ProcessOutputType;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsException;
import git4idea.GitFormatException;
import git4idea.GitUtil;
import git4idea.commands.GitLineHandler;
import git4idea.commands.GitLineHandlerListener;
import git4idea.i18n.GitBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

/**
 * This class processes output of git log command by feeding it line-by-line to the {@link GitLogParser}.
 * It does not store output in order to save memory.
 * Parsed records are passed to the specified {@link Consumer}.
 */
class GitLogOutputSplitter<R extends GitLogRecord> implements GitLineHandlerListener {
  private final @NotNull GitLineHandler myHandler;
  private final @NotNull GitLogParser<R> myParser;
  private final @NotNull Consumer<? super R> myRecordConsumer;

  private final @NotNull @Nls StringBuilder myErrors = new StringBuilder();
  private @Nullable VcsException myException = null;

  GitLogOutputSplitter(@NotNull GitLineHandler handler,
                       @NotNull GitLogParser<R> parser,
                       @NotNull Consumer<? super R> recordConsumer) {
    myHandler = handler;
    myParser = parser;
    myRecordConsumer = recordConsumer;

    myHandler.addLineListener(this);
  }

  @Override
  public void onLineAvailable(@NlsSafe String line, Key outputType) {
    if (ProcessOutputType.isStderr(outputType)) {
      myErrors.append(GitUtil.cleanupErrorPrefixes(line)).append("\n");
    }
    else if (ProcessOutputType.isStdout(outputType)) {
      try {
        processOutputLine(line);
      }
      catch (ProcessCanceledException pce) {
        throw pce;
      }
      catch (VcsException e) {
        myException = e;
      }
      catch (Throwable e) {
        myException = new VcsException(e);
      }
    }
  }

  private void processOutputLine(@NotNull @NlsSafe String line) throws VcsException {
    try {
      R record = myParser.parseLine(line);
      if (record != null) {
        record.setUsedHandler(myHandler);
        myRecordConsumer.accept(record);
      }
    }
    catch (GitFormatException e) {
      myParser.clear();
      throw new VcsException(GitBundle.message("log.parser.exception.message.error.parsing.line",
                                               StringUtil.escapeStringCharacters(line)), e);
    }
  }

  @Override
  public void processTerminated(int exitCode) {
    if (exitCode != 0) {
      String errorMessage = myErrors.toString();
      if (errorMessage.isEmpty()) {
        errorMessage = GitBundle.message("git.error.exit", exitCode);
      }
      myException = new VcsException(GitBundle.message("log.parser.exception.message.error.command.line",
                                                       errorMessage,
                                                       myHandler.printableCommandLine()));
    }
    else {
      try {
        R record = myParser.finish();
        if (record != null) {
          record.setUsedHandler(myHandler);
          myRecordConsumer.accept(record);
        }
      }
      catch (ProcessCanceledException pce) {
        throw pce;
      }
      catch (Throwable t) {
        myException = new VcsException(t);
      }
    }
  }

  @Override
  public void startFailed(@NotNull Throwable exception) {
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
