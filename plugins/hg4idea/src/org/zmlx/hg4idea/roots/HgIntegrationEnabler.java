/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.zmlx.hg4idea.roots;

import com.intellij.openapi.vcs.VcsNotifier;
import com.intellij.openapi.vcs.roots.VcsIntegrationEnabler;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.zmlx.hg4idea.HgVcs;
import org.zmlx.hg4idea.HgVcsMessages;
import org.zmlx.hg4idea.command.HgInitCommand;
import org.zmlx.hg4idea.execution.HgCommandResult;
import org.zmlx.hg4idea.execution.HgCommandResultHandler;
import org.zmlx.hg4idea.util.HgErrorUtil;
import org.zmlx.hg4idea.util.HgUtil;

public class HgIntegrationEnabler extends VcsIntegrationEnabler<HgVcs> {

  public HgIntegrationEnabler(@NotNull HgVcs vcs) {
    super(vcs);
  }

  @Override
  protected boolean initOrNotifyError(@NotNull final VirtualFile projectDir) {
    final boolean[] success = new boolean[1];
    new HgInitCommand(myProject).execute(projectDir, new HgCommandResultHandler() {
      @Override
      public void process(@Nullable HgCommandResult result) {
        VcsNotifier notification = VcsNotifier.getInstance(myProject);
        if (!HgErrorUtil.hasErrorsInCommandExecution(result)) {
          success[0] = true;
          refreshVcsDir(projectDir, HgUtil.DOT_HG);
          notification.notifySuccess(HgVcsMessages.message("hg4idea.init.created.notification.title"),
                                     HgVcsMessages
                                       .message("hg4idea.init.created.notification.description", projectDir.getPresentableUrl())
          );
        }
        else {
          success[0] = false;
          String errors = result != null ? result.getRawError() : "";
          notification.notifyError(
            HgVcsMessages.message("hg4idea.init.error.description", projectDir.getPresentableUrl()), errors);
        }
      }
    });
    return success[0];
  }
}
