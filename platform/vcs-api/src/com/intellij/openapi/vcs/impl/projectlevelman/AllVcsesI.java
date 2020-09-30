// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.impl.projectlevelman;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.impl.VcsDescriptor;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface AllVcsesI {
  void registerManually(@NotNull AbstractVcs vcs);

  void unregisterManually(@NotNull AbstractVcs vcs);

  AbstractVcs getByName(@NotNull @NonNls String name);

  @Nullable
  VcsDescriptor getDescriptor(@NonNls String name);

  VcsDescriptor[] getAll();

  AbstractVcs[] getSupportedVcses();

  boolean isEmpty();

  static AllVcsesI getInstance(@NotNull Project project) {
    return project.getService(AllVcsesI.class);
  }
}
