package com.intellij.jps.cache.hashing;

import com.intellij.openapi.util.io.FileUtilRt;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

class RelativeToDirectoryRelativizer implements PathRelativizer {
  private final Path rootPath;

  RelativeToDirectoryRelativizer(String rootModulePath) {
    this.rootPath = Paths.get(rootModulePath);
  }

  @Override
  public String relativize(File target) {
    return FileUtilRt.toSystemIndependentName(rootPath.relativize(Paths.get(target.getPath())).toString());
  }
}

