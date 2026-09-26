// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.model.dependencyModel;

import org.gradle.api.artifacts.ModuleIdentifier;
import org.jetbrains.annotations.NotNull;

class DefaultModuleIdentifier implements ModuleIdentifier {

  private final @NotNull String myGroup;
  private final @NotNull String myModule;

  DefaultModuleIdentifier(@NotNull String group, @NotNull String module) {
    myGroup = group;
    myModule = module;
  }

  @Override
  public @NotNull String getGroup() {
    return myGroup;
  }

  @Override
  public @NotNull String getName() {
    return myModule;
  }
}
