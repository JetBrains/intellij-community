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
package org.zmlx.hg4idea.provider;

import com.intellij.dvcs.ignore.VcsRepositoryIgnoredFilesHolderBase;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.zmlx.hg4idea.command.HgStatusCommand;
import org.zmlx.hg4idea.repo.HgRepository;
import org.zmlx.hg4idea.repo.HgRepositoryManager;

import java.util.Collection;
import java.util.Set;

public class HgLocalIgnoredHolder extends VcsRepositoryIgnoredFilesHolderBase<HgRepository> {

  public HgLocalIgnoredHolder(@NotNull HgRepository repository, @NotNull HgRepositoryManager repositoryManager) {
    super(repository, repositoryManager, "HgIgnoreUpdate", "hgRescanIgnored");
  }

  @NotNull
  @Override
  protected Set<VirtualFile> requestIgnored(@Nullable Collection<? extends FilePath> paths) {
    Set<VirtualFile> ignored = ContainerUtil.newHashSet();
    ignored.addAll(new HgStatusCommand.Builder(false).ignored(true).build(repository.getProject())
                     .getFiles(repository.getRoot(), paths != null ? ContainerUtil.newArrayList(paths) : null));
    return ignored;
  }

  @Override
  protected boolean scanTurnedOff() {
    return !Registry.is("hg4idea.process.ignored");
  }
}
