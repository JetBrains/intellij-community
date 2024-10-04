// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.impl.projectlevelman;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcs;
import org.jetbrains.annotations.*;

import java.util.Collection;
import java.util.List;

@ApiStatus.Internal
public interface PersistentVcsSetting {
  @NonNls @NotNull String getId();

  @Nls @NotNull String getDisplayName();

  void addApplicableVcs(@Nullable AbstractVcs vcs);

  boolean isApplicableTo(@NotNull Collection<? extends AbstractVcs> vcs);

  @NotNull
  List<AbstractVcs> getApplicableVcses(@NotNull Project project);
}
