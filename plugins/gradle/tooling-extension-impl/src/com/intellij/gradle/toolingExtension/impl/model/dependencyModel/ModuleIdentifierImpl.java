// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.model.dependencyModel;

import org.gradle.api.artifacts.ModuleIdentifier;
import org.jetbrains.annotations.NotNull;

class ModuleIdentifierImpl implements ModuleIdentifier {
  @NotNull private final String myGroup;
  @NotNull private final String myModule;

  ModuleIdentifierImpl(@NotNull String group, @NotNull String module) {
    myGroup = group;
    myModule = module;
  }

  @Override
  public String getGroup() {
    return myGroup;
  }

  @Override
  public String getName() {
    return myModule;
  }
}
