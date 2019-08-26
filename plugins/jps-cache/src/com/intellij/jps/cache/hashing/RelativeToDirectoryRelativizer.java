package com.intellij.jps.cache.hashing;

import java.io.File;
import java.nio.file.Paths;

class RelativeToDirectoryRelativizer implements PathRelativizer {
  private final String rootPath;

  RelativeToDirectoryRelativizer(String rootModulePath) {
    this.rootPath = rootModulePath;
  }

  @Override
  public String relativize(File target) {
    return Paths.get(rootPath).relativize(Paths.get(target.getPath())).toString();
  }
}

