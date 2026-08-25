// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.tooling.internal;

import com.intellij.serialization.PropertyMapping;
import org.jetbrains.plugins.gradle.model.VersionCatalogsModel;

import java.io.File;
import java.io.Serializable;
import java.util.Map;

@SuppressWarnings("IO_FILE_USAGE")
public class VersionCatalogsModelImpl implements VersionCatalogsModel, Serializable {
  private final Map<String, File> catalogsLocations;

  @PropertyMapping({"catalogsLocations"})
  public VersionCatalogsModelImpl(Map<String, File> catalogsLocations) { this.catalogsLocations = catalogsLocations; }

  @Override
  public Map<String, File> getCatalogsLocations() {
    return catalogsLocations;
  }
}
