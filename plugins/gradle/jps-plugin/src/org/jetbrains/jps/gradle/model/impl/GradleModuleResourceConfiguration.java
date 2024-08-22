// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.gradle.model.impl;

import com.dynatrace.hash4j.hashing.HashSink;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xmlb.annotations.OptionTag;
import com.intellij.util.xmlb.annotations.Tag;
import com.intellij.util.xmlb.annotations.XCollection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.incremental.relativizer.PathRelativizerService;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Vladislav.Soroka
 */
public final class GradleModuleResourceConfiguration {
  @Tag("id") public @NotNull ModuleVersion id;

  @Tag("parentId") public @Nullable ModuleVersion parentId;

  @OptionTag
  public boolean overwrite;

  @OptionTag
  public String outputDirectory = null;

  @XCollection(propertyElementName = "resources", elementName = "resource")
  public List<ResourceRootConfiguration> resources = new ArrayList<>();

  @XCollection(propertyElementName = "test-resources", elementName = "resource")
  public List<ResourceRootConfiguration> testResources = new ArrayList<>();

  public void computeConfigurationHash(boolean forTestResources, PathRelativizerService pathRelativizerService, @NotNull HashSink hash) {
    computeModuleConfigurationHash(hash);

    List<ResourceRootConfiguration> _resources = forTestResources ? testResources : resources;
    for (ResourceRootConfiguration resource : _resources) {
      resource.computeConfigurationHash(pathRelativizerService, hash);
    }
    hash.putInt(_resources.size());
  }

  public void computeConfigurationHash(@NotNull HashSink hash) {
    computeModuleConfigurationHash(hash);

    List<ResourceRootConfiguration> _resources = ContainerUtil.concat(testResources, resources);
    for (ResourceRootConfiguration resource : _resources) {
      resource.computeConfigurationHash(null, hash);
    }
    hash.putInt(_resources.size());
  }

  public void computeModuleConfigurationHash(@NotNull HashSink hash) {
    hash.putInt(id.hashCode());
    if (parentId == null) {
      hash.putBoolean(false);
    }
    else {
      hash.putBoolean(true);
      parentId.computeHash(hash);
    }
    if (outputDirectory == null) {
      hash.putInt(-1);
    }
    else {
      hash.putString(outputDirectory);
    }
    hash.putBoolean(overwrite);
  }
}



