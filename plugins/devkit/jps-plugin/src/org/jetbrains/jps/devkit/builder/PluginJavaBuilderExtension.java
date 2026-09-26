// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.devkit.builder;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.builders.java.JavaBuilderExtension;
import org.jetbrains.jps.devkit.model.JpsPluginModuleType;
import org.jetbrains.jps.model.module.JpsModuleType;

import java.util.Set;

public final class PluginJavaBuilderExtension extends JavaBuilderExtension {
  @Override
  public @NotNull Set<? extends JpsModuleType<?>> getCompilableModuleTypes() {
    return Set.of(JpsPluginModuleType.INSTANCE);
  }
}
