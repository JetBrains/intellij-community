// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.lang.ant.config;

import com.intellij.lang.ant.config.impl.BuildFileProperty;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public interface AntBuildTarget {
  @Nullable
  @NlsSafe String getName();

  @Nullable
  @NlsSafe
  String getDisplayName();

  /**
   * @return target names as defined in the underlying ant build file in the order they should be executed
   *         For normal targets this is a singleton list with the target name that getName() method returns. For meta-targets this is
   *         a list of targets that form the meta-target
   */
  default @NotNull List<@NlsSafe String> getTargetNames() {
    final String name = getName();
    return ContainerUtil.createMaybeSingletonList(name);
  }

  @Nullable
  @Nls(capitalization = Nls.Capitalization.Sentence) String getNotEmptyDescription();

  boolean isDefault();

  void run(DataContext dataContext, List<BuildFileProperty> additionalProperties, AntBuildListener buildListener);

  AntBuildModel getModel();
}
