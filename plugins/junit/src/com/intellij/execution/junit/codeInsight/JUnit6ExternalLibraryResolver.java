// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.junit.codeInsight;

import com.intellij.execution.junit.JUnitExternalLibraryDescriptor;
import com.intellij.openapi.roots.ExternalLibraryDescriptor;
import org.jetbrains.annotations.NotNull;

public final class JUnit6ExternalLibraryResolver extends JupiterExternalLibraryResolver {
  @Override
  protected @NotNull ExternalLibraryDescriptor getDescriptor() {
    return JUnitExternalLibraryDescriptor.JUNIT6;
  }
}