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

public final class JUnitExternalLibraryResolver extends ExternalLibraryResolver {
  private static final Set<String> JUNIT4_ANNOTATIONS = Set.of(
    "Test", "Ignore", "RunWith", "Before", "BeforeClass", "After", "AfterClass"
  );
  @Override
  public @Nullable ExternalClassResolveResult resolveClass(@NotNull String shortClassName, @NotNull ThreeState isAnnotation, @NotNull Module contextModule) {
    if ("TestCase".equals(shortClassName)) {
      return new ExternalClassResolveResult("junit.framework.TestCase", JUnitExternalLibraryDescriptor.JUNIT3);
    }
    if (isAnnotation == ThreeState.YES && JUNIT4_ANNOTATIONS.contains(shortClassName)) {
      return new ExternalClassResolveResult("org.junit." + shortClassName, JUnitExternalLibraryDescriptor.JUNIT4);
    }
    return null;
  }

  @Override
  public @Nullable ExternalLibraryDescriptor resolvePackage(@NotNull String packageName) {
    if (packageName.equals("org.junit")) {
      return JUnitExternalLibraryDescriptor.JUNIT4;
    }
    if (packageName.equals("junit.framework")) {
      return JUnitExternalLibraryDescriptor.JUNIT3;
    }
    return null;
  }
}
