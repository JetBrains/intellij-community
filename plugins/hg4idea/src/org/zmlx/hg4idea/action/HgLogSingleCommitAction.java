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

import com.intellij.dvcs.repo.AbstractRepositoryManager;
import com.intellij.dvcs.ui.VcsLogSingleCommitAction;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.zmlx.hg4idea.repo.HgRepository;
import org.zmlx.hg4idea.repo.HgRepositoryManager;

public abstract class HgLogSingleCommitAction extends VcsLogSingleCommitAction<HgRepository> {

  @NotNull
  @Override
  protected AbstractRepositoryManager<HgRepository> getRepositoryManager(@NotNull Project project) {
    return ServiceManager.getService(project, HgRepositoryManager.class);
  }

  @Nullable
  @Override
  protected HgRepository getRepositoryForRoot(@NotNull Project project, @NotNull VirtualFile root) {
    return getRepositoryManager(project).getRepositoryForRoot(root);
  }

}
