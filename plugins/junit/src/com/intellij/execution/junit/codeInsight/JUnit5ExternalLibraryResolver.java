// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.junit.codeInsight;

import com.intellij.codeInsight.daemon.quickFix.ExternalLibraryResolver;
import com.intellij.execution.junit.JUnitExternalLibraryDescriptor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ExternalLibraryDescriptor;
import com.intellij.util.ThreeState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

public final class JUnit5ExternalLibraryResolver extends ExternalLibraryResolver {
  private static final Set<String> JUNIT5_ANNOTATIONS = Set.of(
    "Test", "Disabled", "TestFactory", "BeforeEach", "BeforeAll", "AfterEach", "AfterAll", "DisplayName", "Nested"
  );
  @Override
  public @Nullable ExternalClassResolveResult resolveClass(@NotNull String shortClassName, @NotNull ThreeState isAnnotation, @NotNull Module contextModule) {
    if (isAnnotation == ThreeState.YES && JUNIT5_ANNOTATIONS.contains(shortClassName)) {
      return new ExternalClassResolveResult("org.junit.jupiter.api." + shortClassName, JUnitExternalLibraryDescriptor.JUNIT5);
    }
    return null;
  }

  @Override
  public @Nullable ExternalLibraryDescriptor resolvePackage(@NotNull String packageName) {
    if (packageName.equals("org.junit.jupiter") || packageName.equals("org.junit")) {
      return JUnitExternalLibraryDescriptor.JUNIT5;
    }
    return null;
  }
}
