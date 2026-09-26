// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.devkit.model;

import org.jetbrains.annotations.Nullable;

public class JpsPluginModuleProperties {
  private final String myPluginXmlUrl;
  private final String myManifestFileUrl;

  public JpsPluginModuleProperties(@Nullable String pluginXmlUrl, @Nullable String manifestFileUrl) {
    myPluginXmlUrl = pluginXmlUrl;
    myManifestFileUrl = manifestFileUrl;
  }

  public @Nullable String getPluginXmlUrl() {
    return myPluginXmlUrl;
  }

  public @Nullable String getManifestFileUrl() {
    return myManifestFileUrl;
  }
}
