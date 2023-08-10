// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.facet.mock;

import com.intellij.facet.FacetConfiguration;
import com.intellij.facet.ui.FacetEditorContext;
import com.intellij.facet.ui.FacetEditorTab;
import com.intellij.facet.ui.FacetValidatorsManager;
import com.intellij.openapi.components.PersistentStateComponent;
import org.jetbrains.annotations.NotNull;

public class AnotherMockFacetConfiguration implements FacetConfiguration, PersistentStateComponent<AnotherFacetConfigProperties> {
  @NotNull public AnotherFacetConfigProperties myProperties = new AnotherFacetConfigProperties();

  @Override
  public FacetEditorTab[] createEditorTabs(FacetEditorContext editorContext, FacetValidatorsManager validatorsManager) {
    return new FacetEditorTab[0];
  }

  @NotNull
  @Override
  public AnotherFacetConfigProperties getState() {
    return myProperties;
  }

  @Override
  public void loadState(@NotNull AnotherFacetConfigProperties properties) {
    myProperties = properties;
  }
}
