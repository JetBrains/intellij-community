// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.ant.config.impl;

import com.intellij.util.config.AbstractProperty;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class AntBuildFileClassLoaderHolder extends ClassLoaderHolder {
  public AntBuildFileClassLoaderHolder(AbstractProperty.AbstractPropertyContainer options) {
    super(options);
  }

  @Override
  protected ClassLoader buildClasspath() {
    List<File> files = new ArrayList<>();
    List<Path> paths = new ArrayList<>();
    for (AntClasspathEntry entry : AntBuildFileImpl.ADDITIONAL_CLASSPATH.get(myOptions)) {
      entry.addFilesTo(files);
      for (File file : files) {
        paths.add(file.toPath());
      }
      files.clear();
    }

    final AntInstallation antInstallation = AntBuildFileImpl.RUN_WITH_ANT.get(myOptions);
    final ClassLoader parentLoader = (antInstallation != null) ? antInstallation.getClassLoader() : null;
    if (parentLoader != null && files.size() == 0) {
      // no additional classpath, so it's ok to use ant installation's loader
      return parentLoader;
    }

    return new AntResourcesClassLoader(paths, parentLoader, false, false);
  }
}