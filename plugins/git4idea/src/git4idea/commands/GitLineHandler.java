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
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.EventDispatcher;
import com.intellij.util.io.BaseDataReader;
import com.intellij.util.io.BaseOutputReader;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

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
  private void notifyLine(String line, Key outputType) {
    if (outputType == ProcessOutputTypes.STDOUT) {
      if (!isStdoutSuppressed() && !mySilent && !StringUtil.isEmptyOrSpaces(line)) {
        LOG.info(line.trim());
      }
      else {
        OUTPUT_LOG.debug(line.trim());
      }
    }
    else if (outputType == ProcessOutputTypes.STDERR && !isStderrSuppressed() && !mySilent && !StringUtil.isEmptyOrSpaces(line)) {
      LOG.info(line.trim());
    }
    else {
      LOG.debug(line.trim());
    }

    myLineListeners.getMulticaster().onLineAvailable(line, outputType);
  }

  @Override
  protected ProcessHandler createProcess(@NotNull GeneralCommandLine commandLine) throws ExecutionException {
    return new MyOSProcessHandler(commandLine, myWithMediator) {
      @NotNull
      @Override
      protected BaseOutputReader.Options readerOptions() {
        return new BaseOutputReader.Options() {
          @Override
          public BaseDataReader.SleepingPolicy policy() {
            return BaseDataReader.SleepingPolicy.BLOCKING;
          }

          @Override
          public boolean splitToLines() {
            return true;
          }

          @Override
          public boolean sendIncompleteLines() {
            return false;
          }

          @Override
          public boolean withSeparators() {
            return false;
          }
        };
      }
    };
  }
}
