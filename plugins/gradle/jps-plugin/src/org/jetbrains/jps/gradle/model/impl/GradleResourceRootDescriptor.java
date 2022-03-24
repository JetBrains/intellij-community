// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.gradle.model.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileFilters;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.builders.BuildRootDescriptor;

import java.io.File;
import java.io.FileFilter;

/**
 * @author Vladislav.Soroka
 */
public class GradleResourceRootDescriptor extends BuildRootDescriptor {
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
    final String path = FileUtil.toCanonicalPath(config.directory);
    myFile = new File(path);
    myId = path;
    myIndexInPom = indexInPom;
    myOverwrite = overwrite;
  }

  public ResourceRootConfiguration getConfiguration() {
    return myConfig;
  }

  @Override
  public String getRootId() {
    return myId;
  }

  @Override
  public File getRootFile() {
    return myFile;
  }

  @Override
  public GradleResourcesTarget getTarget() {
    return myTarget;
  }

  @NotNull
  @Override
  public FileFilter createFileFilter() {
    try {
      return new GradleResourceFileFilter(myFile, myConfig);
    }
    catch (Throwable e) {
      LOG.warn("Can not create resource file filter", e);
    }
    return FileFilters.EVERYTHING;
  }

  @Override
  public boolean canUseFileCache() {
    return true;
  }

  public int getIndexInPom() {
    return myIndexInPom;
  }

  public boolean isOverwrite() {
    return myOverwrite;
  }
}
