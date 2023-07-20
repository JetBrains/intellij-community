// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.module.impl;

import com.intellij.platform.workspace.jps.UnloadedModulesNameHolder;

import java.util.Set;

public class UnloadedModulesNameHolderImpl implements UnloadedModulesNameHolder {
  private final Set<String> names;

  public UnloadedModulesNameHolderImpl(Set<String> names) { this.names = names; }

  @Override
  public boolean isUnloaded(String name) {
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
