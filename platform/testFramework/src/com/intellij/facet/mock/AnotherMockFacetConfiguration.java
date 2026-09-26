// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.facet.mock;

import com.intellij.facet.FacetConfiguration;
import com.intellij.facet.ui.FacetEditorContext;
import com.intellij.facet.ui.FacetEditorTab;
import com.intellij.facet.ui.FacetValidatorsManager;
import com.intellij.openapi.components.PersistentStateComponent;
import org.jetbrains.annotations.NotNull;

public final class AnotherMockFacetConfiguration implements FacetConfiguration, PersistentStateComponent<AnotherFacetConfigProperties> {
  public @NotNull AnotherFacetConfigProperties myProperties = new AnotherFacetConfigProperties();

  @Override
  public FacetEditorTab[] createEditorTabs(FacetEditorContext editorContext, FacetValidatorsManager validatorsManager) {
    return new FacetEditorTab[0];
  }

  @Override
  public @NotNull AnotherFacetConfigProperties getState() {
    return myProperties;
  }

  @Override
  public void loadState(@NotNull AnotherFacetConfigProperties properties) {
    myProperties = properties;
  }
}
