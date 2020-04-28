// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.facet.mock;

import com.intellij.facet.Facet;
import com.intellij.facet.FacetType;
import com.intellij.facet.FacetTypeId;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class MockFacetType extends FacetType<MockFacet, MockFacetConfiguration> {
  public static final FacetTypeId<MockFacet> ID = new FacetTypeId<>("mock");

  public MockFacetType() {
    super(ID, "MockFacetId", "MockFacet");
  }

  @Override
  public MockFacetConfiguration createDefaultConfiguration() {
    return new MockFacetConfiguration();
  }

  @Override
  public MockFacet createFacet(@NotNull Module module, final String name, @NotNull MockFacetConfiguration configuration, @Nullable Facet underlyingFacet) {
    return new MockFacet(module, name, configuration);
  }

  @Override
  public boolean isOnlyOneFacetAllowed() {
    return false;
  }

  @Override
  public boolean isSuitableModuleType(final ModuleType moduleType) {
    return true;
  }

  public static MockFacetType getInstance() {
    return findInstance(MockFacetType.class);
  }
}
