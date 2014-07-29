/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

package org.zmlx.hg4idea.action;

import com.intellij.dvcs.ui.VcsLogSingleCommitAction;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcs.log.VcsFullCommitDetails;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.zmlx.hg4idea.repo.HgRepository;
import org.zmlx.hg4idea.repo.HgRepositoryManager;

import java.util.Map;

public abstract class HgLogSingleCommitAction extends VcsLogSingleCommitAction<HgRepository> {

  @Nullable
  @Override
  protected HgRepository getRepositoryForRoot(@NotNull Project project, @NotNull VirtualFile root) {
    return ServiceManager.getService(project, HgRepositoryManager.class).getRepositoryForRoot(root);
  }

  @NotNull
  @Override
  protected Mode getMode() {
    return Mode.SINGLE_COMMIT;
  }

  @Override
  protected void actionPerformed(@NotNull Map<HgRepository, VcsFullCommitDetails> commits) {
    assert commits.size() == 1;
    Map.Entry<HgRepository, VcsFullCommitDetails> entry = commits.entrySet().iterator().next();
    HgRepository repository = entry.getKey();
    VcsFullCommitDetails commit = entry.getValue();
    actionPerformed(repository, commit);
  }

  protected abstract void actionPerformed(@NotNull HgRepository repository, @NotNull VcsFullCommitDetails commit);

}
