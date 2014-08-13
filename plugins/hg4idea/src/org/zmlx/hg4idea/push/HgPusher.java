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
package org.zmlx.hg4idea.push;

import com.intellij.dvcs.push.PushSpec;
import com.intellij.dvcs.push.Pusher;
import com.intellij.dvcs.push.VcsPushOptionValue;
import com.intellij.dvcs.repo.Repository;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.zmlx.hg4idea.command.HgPushCommand;
import org.zmlx.hg4idea.repo.HgRepository;

import java.util.Map;

public class HgPusher extends Pusher {

  @Override
  public void push(@NotNull Map<Repository, PushSpec> pushSpecs, @Nullable VcsPushOptionValue vcsPushOptionValue, boolean force) {
    for (Map.Entry<Repository, PushSpec> entry : pushSpecs.entrySet()) {
      Repository repository = entry.getKey();
      if (repository instanceof HgRepository) {
        HgRepository hgRepository = (HgRepository)repository;
        PushSpec hgSpec = entry.getValue();
        HgTarget destination = (HgTarget)hgSpec.getTarget();
        if (destination == null) {
          continue;
        }
        HgSource source = (HgSource)hgSpec.getSource();
        Project project = repository.getProject();
        final HgPushCommand pushCommand = new HgPushCommand(project, repository.getRoot(), destination.myTarget);
        pushCommand.setIsNewBranch(true); // set always true, because it just allow mercurial to create a new one if needed
        pushCommand.setForce(force);
        if (source.mySource.equals(hgRepository.getCurrentBookmark())) {
          if (vcsPushOptionValue == HgVcsPushOptionValue.Current) {
            pushCommand.setBookmarkName(source.mySource);
          }
          else {
            pushCommand.setRevision(source.mySource);
          }
        }
        else {
          pushCommand.setBranchName(source.mySource);
        }
        org.zmlx.hg4idea.HgPusher.push(project, pushCommand);
      }
    }
  }
}
