/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.openapi.vcs;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.impl.projectlevelman.AllVcses;
import com.intellij.openapi.vcs.impl.projectlevelman.PersistentVcsSetting;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;


@ApiStatus.Internal
public abstract class VcsAbstractSetting implements PersistentVcsSetting {
  private final Set<String> myApplicable = new HashSet<>();

  @Override
  public void addApplicableVcs(AbstractVcs vcs) {
    if (vcs != null) {
      myApplicable.add(vcs.getName());
    }
  }

  @Override
  public boolean isApplicableTo(@NotNull Collection<? extends AbstractVcs> vcs) {
    for (AbstractVcs abstractVcs : vcs) {
      if (myApplicable.contains(abstractVcs.getName())) return true;
    }
    return false;
  }

  @Override
  public @NotNull List<AbstractVcs> getApplicableVcses(@NotNull Project project) {
    return ContainerUtil.mapNotNull(myApplicable, name -> AllVcses.getInstance(project).getByName(name));
  }
}
