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
package org.zmlx.hg4idea.action.mq;

import com.intellij.openapi.project.Project;
import com.intellij.vcs.log.Hash;
import org.jetbrains.annotations.NotNull;
import org.zmlx.hg4idea.action.HgLogSingleCommitAction;
import org.zmlx.hg4idea.repo.HgRepository;

public abstract class HgMqLogAction extends HgLogSingleCommitAction {

  @Override
  protected boolean isVisible(@NotNull Project project, @NotNull HgRepository repository, @NotNull Hash hash) {
    return repository.getRepositoryConfig().isMqUsed() && super.isVisible(project, repository, hash);
  }

  @Override
  protected boolean isEnabled(@NotNull HgRepository repository, @NotNull Hash commit) {
    return repository.getRepositoryConfig().isMqUsed() && super.isEnabled(repository, commit);
  }
}
