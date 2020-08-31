// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.impl.projectlevelman.AllVcses;
import com.intellij.openapi.vcs.impl.projectlevelman.PersistentVcsSetting;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;


public class VcsAbstractSetting implements PersistentVcsSetting {
  protected final String myDisplayName;
  private final Collection<String> myApplicable = new HashSet<>();

  protected VcsAbstractSetting(@NotNull String displayName) {
    myDisplayName = displayName;
  }

  @Override
  public @NotNull String getDisplayName() {
    return myDisplayName;
  }

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
