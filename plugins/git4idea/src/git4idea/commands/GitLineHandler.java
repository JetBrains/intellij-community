// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.commands;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessIOExecutorService;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.LineHandlerHelper;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.EventDispatcher;
import com.intellij.util.io.BaseDataReader;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Future;

import static java.util.Collections.singletonList;

/**
 * The handler that is based on per-line processing of the text.
 */
public class GitLineHandler extends GitTextHandler {
  /**
   * Line listeners
   */
  private final EventDispatcher<GitLineHandlerListener> myLineListeners = EventDispatcher.create(GitLineHandlerListener.class);

  /**
   * Remote url which require authentication
   */
  @NotNull private Collection<String> myUrls = Collections.emptyList();
  @NotNull private GitAuthenticationMode myIgnoreAuthenticationRequest = GitAuthenticationMode.FULL;
  @Nullable private GitAuthenticationGate myAuthenticationGate;

  public GitLineHandler(@NotNull Project project, @NotNull File directory, @NotNull GitCommand command) {
    super(project, directory, command);
  }

  public GitLineHandler(@NotNull Project project,
                        @NotNull VirtualFile vcsRoot,
                        @NotNull GitCommand command) {
    super(project, vcsRoot, command);
  }

  public GitLineHandler(@NotNull Project project,
                        @NotNull VirtualFile vcsRoot,
                        @NotNull GitCommand command,
                        @NotNull List<String> configParameters) {
    super(project, vcsRoot, command, configParameters);
  }

  public GitLineHandler(@Nullable Project project,
                        @NotNull File directory,
                        @NotNull String pathToExecutable,
                        @NotNull GitCommand command,
                        @NotNull List<String> configParameters) {
    super(project, directory, pathToExecutable, command, configParameters);
  }

  public void setUrl(@NotNull String url) {
    setUrls(singletonList(url));
  }

  public void setUrls(@NotNull Collection<String> urls) {
    myUrls = urls;
  }

  @NotNull
  public Collection<String> getUrls() {
    return myUrls;
  }

  protected boolean isRemote() {
    return !myUrls.isEmpty();
  }

  @NotNull
  public GitAuthenticationMode getIgnoreAuthenticationMode() {
    return myIgnoreAuthenticationRequest;
  }

  public void setIgnoreAuthenticationMode(@NotNull GitAuthenticationMode authenticationMode) {
    myIgnoreAuthenticationRequest = authenticationMode;
  }

  @Nullable
  public GitAuthenticationGate getAuthenticationGate() {
    return myAuthenticationGate;
  }

  public void setAuthenticationGate(@NotNull GitAuthenticationGate authenticationGate) {
    myAuthenticationGate = authenticationGate;
  }

  @Override
  protected void processTerminated(final int exitCode) {}

  public void addLineListener(GitLineHandlerListener listener) {
    super.addListener(listener);
    myLineListeners.addListener(listener);
  }

  @Override
  protected void onTextAvailable(String text, Key outputType) {
    notifyLine(text, outputType);
  }

  /**
   * Notify single line
   *
   * @param line       a line to notify
   * @param outputType output type
   */
  private void notifyLine(@NotNull String line, @NotNull Key outputType) {
    String lineWithoutSeparator = LineHandlerHelper.trimLineSeparator(line);
    // do not log git remote progress (progress lines are separated with CR by convention)
    if (!line.endsWith("\r")) logOutput(lineWithoutSeparator, outputType);
    if (outputType == ProcessOutputTypes.SYSTEM) return;
    myLineListeners.getMulticaster().onLineAvailable(lineWithoutSeparator, outputType);
  }

  private void logOutput(@NotNull String line, @NotNull Key outputType) {
    String trimmedLine = line.trim();
    if (!StringUtil.isEmptyOrSpaces(trimmedLine) &&
        !mySilent &&
        ((outputType == ProcessOutputTypes.STDOUT && !isStdoutSuppressed()) ||
         outputType == ProcessOutputTypes.STDERR && !isStderrSuppressed())) {
      LOG.info(trimmedLine);
    }
    else {
      OUTPUT_LOG.debug(trimmedLine);
    }
  }

  @Override
  protected OSProcessHandler createProcess(@NotNull GeneralCommandLine commandLine) throws ExecutionException {
    return new MyOSProcessHandler(commandLine, myWithMediator && Registry.is("git.execute.with.mediator")) {
      @NotNull
      @Override
      protected BaseDataReader createOutputDataReader() {
        return new LineReader(createProcessOutReader(),
                              readerOptions().policy(),
                              new BufferingTextSplitter((line) -> this.notifyTextAvailable(line, ProcessOutputTypes.STDOUT)),
                              myPresentableName);
      }

      @NotNull
      @Override
      protected BaseDataReader createErrorDataReader() {
        return new LineReader(createProcessErrReader(),
                              readerOptions().policy(),
                              new BufferingTextSplitter((line) -> this.notifyTextAvailable(line, ProcessOutputTypes.STDERR)),
                              myPresentableName);
      }
    };
  }

  public void overwriteConfig(String ... params) {
    for (String param : params) {
      myCommandLine.getParametersList().prependAll("-c", param);
    }
  }

  /**
   * Will not react to {@link com.intellij.util.io.BaseOutputReader.Options}
   * other than {@link com.intellij.util.io.BaseOutputReader.Options#policy()} because we do not negotiate with terrorists
   */
  private static class LineReader extends BaseDataReader {
    @NotNull private final Reader myReader;
    private final char @NotNull [] myInputBuffer = new char[8192];

    @NotNull private final BufferingTextSplitter myOutputProcessor;

    LineReader(@NotNull Reader reader,
                      @NotNull SleepingPolicy sleepingPolicy,
                      @NotNull BufferingTextSplitter outputProcessor,
                      @NotNull String presentableName) {
      super(sleepingPolicy);
      myReader = reader;
      myOutputProcessor = outputProcessor;
      start(presentableName);
    }

    @Override
    protected boolean readAvailableNonBlocking() throws IOException {
      return read(true);
    }

    @Override
    protected boolean readAvailableBlocking() throws IOException {
      return read(false);
    }

    private boolean read(boolean checkReaderReady) throws IOException {
      boolean read = false;
      while (true) {
        if (checkReaderReady && !myReader.ready()) break;
        int n = myReader.read(myInputBuffer);
        if (n < 0) break;
        if (n > 0) {
          read = true;
          myOutputProcessor.process(myInputBuffer, n);
        }
      }
      return read;
    }


    @NotNull
    @Override
    protected Future<?> executeOnPooledThread(@NotNull Runnable runnable) {
      return ProcessIOExecutorService.INSTANCE.submit(runnable);
    }

    @Override
    protected void close() throws IOException {
      try {
        myReader.close();
      }
      finally {
        myOutputProcessor.flush();
      }
    }
  }
}
