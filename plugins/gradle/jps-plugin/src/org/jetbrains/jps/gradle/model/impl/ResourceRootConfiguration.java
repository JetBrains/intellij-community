// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.gradle.model.impl;

import com.dynatrace.hash4j.hashing.HashFunnel;
import com.dynatrace.hash4j.hashing.HashSink;
import com.dynatrace.hash4j.hashing.Hashing;
import com.intellij.util.xmlb.annotations.Attribute;
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
@Tag("resource")
public final class ResourceRootConfiguration extends FilePattern {
  @Tag("directory") public @NotNull String directory;

  @Tag("targetPath") public @Nullable String targetPath;

  @Attribute("filtered")
  public boolean isFiltered;

  @XCollection(propertyElementName = "filters", elementName = "filter")
  public List<ResourceRootFilter> filters = new ArrayList<>();

  public void computeConfigurationHash(@Nullable PathRelativizerService pathRelativizerService, @NotNull HashSink hash) {
    if (pathRelativizerService == null) {
      hash.putString(directory);
      if (targetPath == null) {
        hash.putInt(-1);
      }
      else {
        hash.putString(targetPath);
      }
    }
    else {
      hash.putString(pathRelativizerService.toRelative(directory));
      if (targetPath == null) {
        hash.putInt(-1);
      }
      else {
        hash.putString(pathRelativizerService.toRelative(targetPath));
      }
    }

    hash.putBoolean(isFiltered);
    hash.putUnorderedIterable(includes, HashFunnel.forString(), Hashing.komihash5_0());
    hash.putUnorderedIterable(excludes, HashFunnel.forString(), Hashing.komihash5_0());

    for (ResourceRootFilter filter : filters) {
      filter.computeConfigurationHash(hash);
    }
    hash.putInt(filters.size());
  }
}
