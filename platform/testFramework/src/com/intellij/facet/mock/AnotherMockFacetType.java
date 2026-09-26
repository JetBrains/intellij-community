// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.facet.mock;

import com.intellij.facet.Facet;
import com.intellij.facet.FacetType;
import com.intellij.facet.FacetTypeId;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class AnotherMockFacetType extends FacetType<AnotherMockFacet, AnotherMockFacetConfiguration> {
  public static final FacetTypeId<AnotherMockFacet> ID = new FacetTypeId<>("another_mock");

  public AnotherMockFacetType() {
    super(ID, "AnotherMockFacetId", "AnotherMockFacet");
  }

  @Override
  public AnotherMockFacetConfiguration createDefaultConfiguration() {
    return new AnotherMockFacetConfiguration();
  }

  @Override
  public AnotherMockFacet createFacet(@NotNull Module module, final String name, @NotNull AnotherMockFacetConfiguration configuration, @Nullable Facet underlyingFacet) {
    return new AnotherMockFacet(module, name, configuration);
  }

  @Override
  public boolean isOnlyOneFacetAllowed() {
    return false;
  }

  @Override
  public boolean isSuitableModuleType(final ModuleType moduleType) {
    return true;
  }

  public static AnotherMockFacetType getInstance() {
    return findInstance(AnotherMockFacetType.class);
  }
}
