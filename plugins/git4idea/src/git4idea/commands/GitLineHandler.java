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
package git4idea.commands;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessIOExecutorService;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
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
  private final boolean myWithMediator;

  /**
   * Remote url which require authentication
   */
  @NotNull private Collection<String> myUrls = Collections.emptyList();
  private boolean myIgnoreAuthenticationRequest;

  public GitLineHandler(@NotNull Project project, @NotNull File directory, @NotNull GitCommand command) {
    super(project, directory, command);
    myWithMediator = true;
  }

  public GitLineHandler(@NotNull Project project,
                        @NotNull VirtualFile vcsRoot,
                        @NotNull GitCommand command) {
    this(project, vcsRoot, command, Collections.emptyList());
  }

  public GitLineHandler(@NotNull Project project,
                        @NotNull VirtualFile vcsRoot,
                        @NotNull GitCommand command,
                        @NotNull List<String> configParameters) {
    this(project, vcsRoot, command, configParameters, true);
  }

  public GitLineHandler(@NotNull Project project,
                        @NotNull VirtualFile vcsRoot,
                        @NotNull GitCommand command,
                        @NotNull List<String> configParameters,
                        boolean withMediator) {
    super(project, vcsRoot, command, configParameters);
    myWithMediator = withMediator;
  }

  public GitLineHandler(@Nullable Project project,
                        @NotNull File directory,
                        @NotNull String pathToExecutable,
                        @NotNull GitCommand command,
                        @NotNull List<String> configParameters) {
    super(project, directory, pathToExecutable, command, configParameters);
    myWithMediator = true;
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

  public boolean isIgnoreAuthenticationRequest() {
    return myIgnoreAuthenticationRequest;
  }

  public void setIgnoreAuthenticationRequest(boolean ignoreAuthenticationRequest) {
    myIgnoreAuthenticationRequest = ignoreAuthenticationRequest;
  }

  protected void processTerminated(final int exitCode) {}

  public void addLineListener(GitLineHandlerListener listener) {
    super.addListener(listener);
    myLineListeners.addListener(listener);
  }

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
    myLineListeners.getMulticaster().onLineAvailable(lineWithoutSeparator, outputType);
  }

  private void logOutput(@NotNull String line, @NotNull Key outputType) {
    String trimmedLine = line.trim();
    if (outputType == ProcessOutputTypes.STDOUT) {
      if (!isStdoutSuppressed() && !mySilent && !StringUtil.isEmptyOrSpaces(trimmedLine)) {
        LOG.info(trimmedLine);
      }
      else {
        OUTPUT_LOG.debug(trimmedLine);
      }
    }
    else if (outputType == ProcessOutputTypes.STDERR && !isStderrSuppressed() && !mySilent && !StringUtil.isEmptyOrSpaces(trimmedLine)) {
      LOG.info(trimmedLine);
    }
    else {
      LOG.debug(trimmedLine);
    }
  }

  @Override
  protected ProcessHandler createProcess(@NotNull GeneralCommandLine commandLine) throws ExecutionException {
    return new MyOSProcessHandler(commandLine, myWithMediator) {
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

  /**
   * Will not react to {@link com.intellij.util.io.BaseOutputReader.Options}
   * other then {@link com.intellij.util.io.BaseOutputReader.Options#policy()} because we do not negotiate with terrorists
   */
  private static class LineReader extends BaseDataReader {
    @NotNull private final Reader myReader;
    @NotNull private final char[] myInputBuffer = new char[8192];

    @NotNull private final BufferingTextSplitter myOutputProcessor;

    public LineReader(@NotNull Reader reader,
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
      try {
        int n;
        while (true) {
          if (checkReaderReady && !myReader.ready()) break;
          if ((n = myReader.read(myInputBuffer)) < 0) break;
          if (n > 0) {
            read = true;
            myOutputProcessor.process(myInputBuffer, n);
          }
        }
      }
      finally {
        myOutputProcessor.flush();
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
      myReader.close();
    }
  }
}
