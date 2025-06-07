// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.tooling.internal.web;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.model.web.WebConfiguration;

import java.util.List;

/**
 * @author Vladislav.Soroka
 */
public class WebConfigurationImpl implements WebConfiguration {

  private final @NotNull List<? extends WarModel> myWarModels;

  public WebConfigurationImpl(@NotNull List<? extends WarModel> warModels) {
    myWarModels = warModels;
  }

  @Override
  public List<? extends WarModel> getWarModels() {
    return myWarModels;
  }
}
