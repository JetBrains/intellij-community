// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.ant.config.impl;

import com.intellij.util.config.AbstractProperty;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class AntInstallationClassLoaderHolder extends ClassLoaderHolder {
  public AntInstallationClassLoaderHolder(AbstractProperty.AbstractPropertyContainer options) {
    super(options);
  }

  @Override
  protected ClassLoader buildClasspath() {
    List<File> files = new ArrayList<>();
    // ant installation jars
    List<AntClasspathEntry> cp = AntInstallation.CLASS_PATH.get(myOptions);
    for (final AntClasspathEntry entry : cp) {
      entry.addFilesTo(files);
    }

    // jars from user home
    files.addAll(AntBuildFileImpl.getUserHomeLibraries());

    List<Path> paths = new ArrayList<>(files.size());
    for (File file : files) {
      paths.add(file.toPath());
    }
    return new AntResourcesClassLoader(paths, null, true, false);
  }
}