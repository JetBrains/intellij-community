/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.util.BackgroundTaskUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread;
import com.intellij.util.containers.ContainerUtil;
import git4idea.GitDisposable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class GitTask {
  private final @NotNull Project myProject;
  private final @NotNull GitLineHandler myHandler;
  private final @NlsContexts.ProgressTitle String myTitle;
  private final @Nullable GitProgressAnalyzer myProgressAnalyzer;
  private final @NotNull ProgressIndicator myProgressIndicator;

  public GitTask(@NotNull Project project,
                 @NotNull GitLineHandler handler,
                 @NotNull @NlsContexts.ProgressTitle String title,
                 @NotNull ProgressIndicator progressIndicator,
                 @Nullable GitProgressAnalyzer progressAnalyzer) {
    myProject = project;
    myHandler = handler;
    myTitle = title;
    myProgressIndicator = progressIndicator;
    myProgressAnalyzer = progressAnalyzer;
  }

  @RequiresBackgroundThread
  public @NotNull GitCommandResult executeInCurrentThread() {
    String oldTitle = myProgressIndicator.getText();
    try {
      myProgressIndicator.setText(myTitle);
      myProgressIndicator.setText2("");
      myProgressIndicator.setIndeterminate(myProgressAnalyzer == null);
      myHandler.addLineListener(new MyProgressGitLineListener());

      return BackgroundTaskUtil.runUnderDisposeAwareIndicator(GitDisposable.getInstance(myProject),
                                                              () -> Git.getInstance().runCommand(myHandler),
                                                              myProgressIndicator);
    }
    finally {
      myProgressIndicator.setText(oldTitle);
    }
  }

  public static @NotNull List<@NlsSafe String> collectErrorOutputLines(@NotNull GitCommandResult result) {
    List<String> errors = new ArrayList<>();
    errors.addAll(ContainerUtil.filter(result.getOutput(), line -> GitHandlerUtil.isErrorLine(line.trim())));
    errors.addAll(ContainerUtil.filter(result.getErrorOutput(), line -> GitHandlerUtil.isErrorLine(line.trim())));

    if (errors.isEmpty() && !result.success()) {
      errors.addAll(result.getErrorOutput());
      if (errors.isEmpty()) {
        List<String> output = result.getOutput();
        String lastOutput = ContainerUtil.findLast(output, line -> !StringUtil.isEmptyOrSpaces(line));
        return Collections.singletonList(lastOutput);
      }
    }
    return errors;
  }

  public static @NotNull GitTaskResult getTaskResult(@NotNull GitCommandResult result) {
    if (result.cancelled()) {
      return GitTaskResult.CANCELLED;
    }
    else if (!result.success() ||
             !collectErrorOutputLines(result).isEmpty()) {
      return GitTaskResult.GIT_ERROR;
    }
    else {
      return GitTaskResult.OK;
    }
  }

  private class MyProgressGitLineListener implements GitLineHandlerListener {
    @Override
    public void onLineAvailable(String line, Key outputType) {
      myProgressIndicator.setText2(line);

      if (myProgressAnalyzer != null) {
        double fraction = myProgressAnalyzer.analyzeProgress(line);
        if (fraction >= 0) {
          myProgressIndicator.setFraction(fraction);
        }
      }
    }
  }
}
