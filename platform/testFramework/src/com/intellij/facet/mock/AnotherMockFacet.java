// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.facet.mock;

import com.intellij.facet.Facet;
import com.intellij.openapi.module.Module;
import org.jetbrains.annotations.NotNull;

public class AnotherMockFacet extends Facet<AnotherMockFacetConfiguration> {
  private boolean myInitialized;
  private boolean myDisposed;
  private boolean myConfigured;

  public AnotherMockFacet(@NotNull final Module module, final String name) {
    this(module, name, new AnotherMockFacetConfiguration());
  }

  public AnotherMockFacet(final Module module, String name, final AnotherMockFacetConfiguration configuration) {
    super(AnotherMockFacetType.getInstance(), module, name, configuration, null);
  }

  @Override
  public void initFacet() {
    myInitialized = true;
  }

  @Override
  public void disposeFacet() {
    myDisposed = true;
  }

  public boolean isConfigured() {
    return myConfigured;
  }

  public void configure() {
    myConfigured = true;
  }

  public boolean isInitialized() {
    return myInitialized;
  }

  @Override
  public boolean isDisposed() {
    return myDisposed;
  }
}
