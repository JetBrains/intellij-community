// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.module.impl;

import com.intellij.platform.workspace.jps.UnloadedModulesNameHolder;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

@ApiStatus.Internal
public class UnloadedModulesNameHolderImpl implements UnloadedModulesNameHolder {
  private final Set<String> names;

  public UnloadedModulesNameHolderImpl(Set<String> names) { this.names = names; }

  @Override
  public boolean isUnloaded(@NotNull String name) {
    return names.contains(name);
  }

  @Override
  public boolean hasUnloaded() {
    return !names.isEmpty();
  }


  @Override
  public String toString() {
    return names.toString();
  }
}
