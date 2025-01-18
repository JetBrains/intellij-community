// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.facet.mock;

import com.intellij.facet.Facet;
import com.intellij.facet.FacetType;
import com.intellij.facet.FacetTypeId;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class MockSubFacetType extends FacetType<Facet, MockFacetConfiguration> {
  public static final FacetTypeId<Facet> ID = new FacetTypeId<>("submock");

  public MockSubFacetType() {
    super(ID, "subFacetId", "sub facet", MockFacetType.ID);
  }

  @Override
  public MockFacetConfiguration createDefaultConfiguration() {
    return new MockFacetConfiguration();
  }

  @Override
  public Facet createFacet(@NotNull Module module, final String name, @NotNull MockFacetConfiguration configuration, @Nullable Facet underlyingFacet) {
    return new Facet<>(this, module, name, configuration, underlyingFacet);
  }

  @Override
  public boolean isSuitableModuleType(final ModuleType moduleType) {
    return true;
  }

  @Override
  public boolean isOnlyOneFacetAllowed() {
    return false;
  }

  public static MockSubFacetType getInstance() {
    return findInstance(MockSubFacetType.class);
  }
}
