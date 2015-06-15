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

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.zmlx.hg4idea.action.HgCommandResultNotifier;
import org.zmlx.hg4idea.execution.HgCommandExecutor;
import org.zmlx.hg4idea.execution.HgCommandResult;
import org.zmlx.hg4idea.execution.HgCommandResultHandler;
import org.zmlx.hg4idea.repo.HgRepository;
import org.zmlx.hg4idea.util.HgErrorUtil;

import java.util.Arrays;

public class HgQPushCommand {
  @NotNull private final HgRepository myRepository;

  public HgQPushCommand(@NotNull HgRepository repository) {
    myRepository = repository;
  }

  public void execute(@NotNull final String patchName) {
    final Project project = myRepository.getProject();
    new HgCommandExecutor(project)
      .execute(myRepository.getRoot(), "qpush", Arrays.asList("--move", patchName), new HgCommandResultHandler() {
        @Override
        public void process(@Nullable HgCommandResult result) {
          if (HgErrorUtil.hasErrorsInCommandExecution(result)) {
            new HgCommandResultNotifier(project)
              .notifyError(result, "QPush command failed", "Could not apply selected patch " + patchName);
          }
          myRepository.update();
        }
      });
  }
}
