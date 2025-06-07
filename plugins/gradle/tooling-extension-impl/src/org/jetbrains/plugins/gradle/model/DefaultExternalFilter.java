// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.model;

import org.jetbrains.annotations.NotNull;

public final class DefaultExternalFilter implements ExternalFilter {
  private static final long serialVersionUID = 1L;

  private @NotNull String filterType;
  private @NotNull String propertiesAsJsonMap;

  public DefaultExternalFilter() {
    propertiesAsJsonMap = "";
    filterType = "";
  }


  public DefaultExternalFilter(ExternalFilter filter) {
    propertiesAsJsonMap = filter.getPropertiesAsJsonMap();
    filterType = filter.getFilterType();
  }

  @Override
  public @NotNull String getFilterType() {
    return filterType;
  }

  public void setFilterType(@NotNull String filterType) {
    this.filterType = filterType;
  }

  @Override
  public @NotNull String getPropertiesAsJsonMap() {
    return propertiesAsJsonMap;
  }

  public void setPropertiesAsJsonMap(@NotNull String propertiesAsJsonMap) {
    this.propertiesAsJsonMap = propertiesAsJsonMap;
  }
}
