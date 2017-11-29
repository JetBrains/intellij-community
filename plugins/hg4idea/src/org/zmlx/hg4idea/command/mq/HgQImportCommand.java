/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package org.zmlx.hg4idea.command.mq;

import com.intellij.openapi.progress.util.BackgroundTaskUtil;
import com.intellij.openapi.project.Project;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.zmlx.hg4idea.action.HgCommandResultNotifier;
import org.zmlx.hg4idea.execution.HgCommandExecutor;
import org.zmlx.hg4idea.execution.HgCommandResult;
import org.zmlx.hg4idea.repo.HgRepository;
import org.zmlx.hg4idea.util.HgErrorUtil;

import java.util.List;

public class HgQImportCommand {

  @NotNull private final HgRepository myRepository;

  public HgQImportCommand(@NotNull HgRepository repository) {
    myRepository = repository;
  }

  public void execute(@NotNull final String startRevisionNumber) {
    BackgroundTaskUtil.executeOnPooledThread(myRepository.getProject(), () -> executeInCurrentThread(startRevisionNumber));
  }

  public void executeInCurrentThread(@NotNull final String startRevisionNumber) {
    final Project project = myRepository.getProject();
    String lastRevisionName = myRepository.getMQAppliedPatches().isEmpty() ? "tip" : "qparent";
    List<String> arguments = ContainerUtil.newArrayList("--rev", startRevisionNumber + ":" + lastRevisionName);
    HgCommandResult result = new HgCommandExecutor(project).executeInCurrentThread(myRepository.getRoot(), "qimport", arguments);
    if (HgErrorUtil.hasErrorsInCommandExecution(result)) {
      new HgCommandResultNotifier(project)
        .notifyError(result, "Import failed", "Import revision from " + startRevisionNumber + " to qparent failed");
    }
    myRepository.update();
  }
}
