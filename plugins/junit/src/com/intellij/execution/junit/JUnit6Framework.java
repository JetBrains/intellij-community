// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.junit;

import com.intellij.openapi.roots.ExternalLibraryDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

import static com.siyeh.ig.junit.JUnitCommonClassNames.ORG_JUNIT_JUPITER_API_METHOD_ORDERER_DEFAULT;

public class JUnit6Framework extends JUnit5Framework {
  @Override
  public boolean isDumbAware() {
    return this.getClass().isAssignableFrom(JUnit6Framework.class);
  }

  @Override
  public @NotNull String getName() {
    return "JUnit6";
  }

  @Override
  protected Collection<String> getMarkerClassFQNames() {
    return List.of(ORG_JUNIT_JUPITER_API_METHOD_ORDERER_DEFAULT, "org.junit.platform.commons.annotation.Contract");
  }

  @Override
  protected String getMarkerClassFQName() {
    return ORG_JUNIT_JUPITER_API_METHOD_ORDERER_DEFAULT;
  }

  @Override
  public @Nullable ExternalLibraryDescriptor getFrameworkLibraryDescriptor() {
    return JUnitExternalLibraryDescriptor.JUNIT6;
  }
}
