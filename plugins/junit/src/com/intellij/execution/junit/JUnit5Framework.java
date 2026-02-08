// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.junit;

import com.intellij.openapi.roots.ExternalLibraryDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

public class JUnit5Framework extends JupiterFramework {
  @Override
  public boolean isDumbAware() {
    // Only Java is available in dumb mode, other language implementation might not support it.
    // For example, Kotlin, because it relies on light classes which require resolve.
    return this.getClass().isAssignableFrom(JUnit5Framework.class);
  }

  @Override
  public @NotNull String getName() {
    return "JUnit5";
  }

  @Override
  protected Collection<String> getMarkerClassFQNames() {
    return List.of(JUnitUtil.TEST5_ANNOTATION, JUnitUtil.CUSTOM_TESTABLE_ANNOTATION);
  }

  @Override
  protected String getMarkerClassFQName() {
    return JUnitUtil.TEST5_ANNOTATION;
  }

  @Override
  public @Nullable ExternalLibraryDescriptor getFrameworkLibraryDescriptor() {
    return JUnitExternalLibraryDescriptor.JUNIT5;
  }
}
