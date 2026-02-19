// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.gradle.model.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileFilters;
import com.intellij.openapi.util.io.FileUtilRt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.builders.BuildRootDescriptor;

import java.io.File;
import java.io.FileFilter;

/**
 * @author Vladislav.Soroka
 */
public final class GradleResourceRootDescriptor extends BuildRootDescriptor {
  private static final Logger LOG = Logger.getInstance(GradleResourceRootDescriptor.class);
  private final GradleResourcesTarget myTarget;
  private final ResourceRootConfiguration myConfig;
  private final File myFile;
  private final String myId;
  private final boolean myOverwrite;

  private final int myIndexInPom;

  public GradleResourceRootDescriptor(@NotNull GradleResourcesTarget target,
                                      ResourceRootConfiguration config,
                                      int indexInPom,
                                      boolean overwrite) {
    myTarget = target;
    myConfig = config;
    final String path = FileUtilRt.toCanonicalPath(config.directory, File.separatorChar, true);
    myFile = new File(path);
    myId = path;
    myIndexInPom = indexInPom;
    myOverwrite = overwrite;
  }

  public ResourceRootConfiguration getConfiguration() {
    return myConfig;
  }

  @Override
  public @NotNull String getRootId() {
    return myId;
  }

  @Override
  public @NotNull File getRootFile() {
    return myFile;
  }

  @Override
  public @NotNull GradleResourcesTarget getTarget() {
    return myTarget;
  }

  @Override
  public @NotNull FileFilter createFileFilter() {
    try {
      return new GradleResourceFileFilter(myFile, myConfig);
    }
    catch (Throwable e) {
      LOG.warn("Can not create resource file filter", e);
    }
    return FileFilters.EVERYTHING;
  }

  public int getIndexInPom() {
    return myIndexInPom;
  }

  public boolean isOverwrite() {
    return myOverwrite;
  }
}
