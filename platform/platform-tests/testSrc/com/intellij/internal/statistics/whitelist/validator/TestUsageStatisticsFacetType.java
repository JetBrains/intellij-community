// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistics.whitelist.validator;

import com.intellij.facet.Facet;
import com.intellij.facet.FacetConfiguration;
import com.intellij.facet.FacetType;
import com.intellij.facet.FacetTypeId;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.extensions.ExtensionPoint;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import junit.framework.TestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@SuppressWarnings("unchecked")
public class TestUsageStatisticsFacetType extends FacetType {

  public static void registerTestFacetType(Disposable disposable) {
    final ExtensionPoint<FacetType> ep = Extensions.getRootArea().getExtensionPoint(FacetType.EP_NAME);
    TestCase.assertNotNull(ep);

    final FacetTypeId id = new FacetTypeId("DebugUsageStatisticsFacet");
    final TestUsageStatisticsFacetType facetType =
      new TestUsageStatisticsFacetType(id, "TestUsageStatisticsFacet", "Mock Facet for Testing");
    ep.registerExtension(facetType, disposable);
  }

  public static void registerTestFacetTypeWithSpace(Disposable disposable) {
    final ExtensionPoint<FacetType> ep = Extensions.getRootArea().getExtensionPoint(FacetType.EP_NAME);
    TestCase.assertNotNull(ep);

    final FacetTypeId id = new FacetTypeId("DebugUsageStatisticsFacet");
    final TestUsageStatisticsFacetType facetType =
      new TestUsageStatisticsFacetType(id, "Test Usage Statistics Facet", "Mock Facet for Testing");
    ep.registerExtension(facetType, disposable);
  }

  public TestUsageStatisticsFacetType(@NotNull FacetTypeId id,
                                      @NotNull String stringId,
                                      @NotNull String presentableName) {
    super(id, stringId, presentableName);
  }

  @Override
  public FacetConfiguration createDefaultConfiguration() {
    return null;
  }

  @Override
  public Facet createFacet(@NotNull Module module,
                           String name,
                           @NotNull FacetConfiguration configuration,
                           @Nullable Facet underlyingFacet) {
    return new TestUsageStatisticsFacet(this, module, name, configuration, underlyingFacet);
  }

  @Override
  public boolean isSuitableModuleType(ModuleType moduleType) {
    return false;
  }

  private static final class TestUsageStatisticsFacet extends Facet {

    private TestUsageStatisticsFacet(@NotNull FacetType facetType,
                                     @NotNull Module module,
                                     @NotNull String name,
                                     @NotNull FacetConfiguration configuration, Facet underlyingFacet) {
      super(facetType, module, name, configuration, underlyingFacet);
    }
  }
}
