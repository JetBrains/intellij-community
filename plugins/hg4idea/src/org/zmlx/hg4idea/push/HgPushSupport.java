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

import com.intellij.dvcs.push.*;
import com.intellij.dvcs.repo.Repository;
import com.intellij.dvcs.repo.RepositoryManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.util.Function;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.zmlx.hg4idea.HgVcs;
import org.zmlx.hg4idea.repo.HgRepository;
import org.zmlx.hg4idea.util.HgUtil;

import java.util.Collection;

public class HgPushSupport extends PushSupport<HgRepository> {

  @NotNull private final Project myProject;
  @NotNull private final HgVcs myVcs;

  public HgPushSupport(@NotNull Project project) {
    myProject = project;
    myVcs = ObjectUtils.assertNotNull(HgVcs.getInstance(myProject));
  }

  @NotNull
  @Override
  public AbstractVcs getVcs() {
    return myVcs;
  }

  @NotNull
  @Override
  public Pusher getPusher() {
    return new HgPusher();
  }

  @NotNull
  @Override
  public OutgoingCommitsProvider getOutgoingCommitsProvider() {
    return new HgOutgoingCommitsProvider();
  }

  @Nullable
  @Override
  public HgTarget getDefaultTarget(@NotNull HgRepository repository) {
    String defaultPushPath = repository.getRepositoryConfig().getDefaultPushPath();
    return defaultPushPath == null ? null : new HgTarget(defaultPushPath);
  }

  @NotNull
  @Override
  public Collection<String> getTargetNames(@NotNull HgRepository repository) {
    return ContainerUtil.map(repository.getRepositoryConfig().getPaths(), new Function<String, String>() {
      @Override
      public String fun(String s) {
        return HgUtil.removePasswordIfNeeded(s);
      }
    });
  }

  @NotNull
  @Override
  public HgPushSource getSource(@NotNull HgRepository repository) {
    String localBranch = HgUtil.getActiveBranchName(repository);
    return new HgPushSource(localBranch);
  }

  @Override
  public HgTarget createTarget(@NotNull HgRepository repository, @NotNull String targetName) {
    return new HgTarget(targetName);
  }

  @NotNull
  @Override
  public RepositoryManager<HgRepository> getRepositoryManager() {
    return HgUtil.getRepositoryManager(myProject);
  }

  @Nullable
  public VcsPushOptionsPanel getVcsPushOptionsPanel() {
    return new HgPushOptionsPanel();
  }

  @Override
  @Nullable
  public VcsError validate(@NotNull Repository repository, @Nullable String targetToValidate) {
    return StringUtil.isEmptyOrSpaces(targetToValidate) ? new VcsError("Please, specify remote push path for repository!") : null;
  }
}
