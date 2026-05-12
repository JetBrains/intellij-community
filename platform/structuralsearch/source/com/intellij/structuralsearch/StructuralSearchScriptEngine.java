// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.structuralsearch;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

@ApiStatus.Internal
public interface StructuralSearchScriptEngine {
  ExtensionPointName<StructuralSearchScriptEngine> EP_NAME = ExtensionPointName.create("com.intellij.structuralsearch.scriptEngine");

  static boolean isAvailable() {
    return EP_NAME.hasAnyExtensions();
  }

  @NotNull CompiledScript compile(@NotNull Project project,
                                  @NotNull String scriptName,
                                  @NotNull String scriptText,
                                  @NotNull MatchOptions matchOptions) throws MalformedPatternException;

  interface CompiledScript {
    @Nullable Object evaluate(@NotNull Map<String, Object> variables) throws Throwable;
  }
}
