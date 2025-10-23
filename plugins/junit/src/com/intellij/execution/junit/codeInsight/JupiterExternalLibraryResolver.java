// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.junit.codeInsight;

import com.intellij.codeInsight.daemon.quickFix.ExternalLibraryResolver;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ExternalLibraryDescriptor;
import com.intellij.util.ThreeState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

abstract class JupiterExternalLibraryResolver extends ExternalLibraryResolver {
  private static final Set<String> JUPITER_ANNOTATIONS = Set.of(
    "Test", "Disabled", "TestFactory", "BeforeEach", "BeforeAll", "AfterEach", "AfterAll", "DisplayName", "Nested"
  );
  @Override
  public @Nullable ExternalClassResolveResult resolveClass(@NotNull String shortClassName, @NotNull ThreeState isAnnotation, @NotNull Module contextModule) {
    if (isAnnotation == ThreeState.YES && JUPITER_ANNOTATIONS.contains(shortClassName)) {
      return new ExternalClassResolveResult("org.junit.jupiter.api." + shortClassName, getDescriptor());
    }
    return null;
  }

  protected abstract @NotNull ExternalLibraryDescriptor getDescriptor();

  @Override
  public @Nullable ExternalLibraryDescriptor resolvePackage(@NotNull String packageName) {
    if (packageName.equals("org.junit.jupiter") || packageName.equals("org.junit")) {
      return getDescriptor();
    }
    return null;
  }
}