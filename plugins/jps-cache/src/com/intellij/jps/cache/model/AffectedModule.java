package com.intellij.jps.cache.model;

import java.io.File;

public class AffectedModule {
  private final String type;
  private final String name;
  private final String hash;
  private final File outPath;

  public AffectedModule(String type, String name, String hash, File outPath) {
    this.type = type;
    this.name = name;
    this.hash = hash;
    this.outPath = outPath;
  }

  public String getType() {
    return type;
  }

  public String getName() {
    return name;
  }

  public String getHash() {
    return hash;
  }

  public File getOutPath() {
    return outPath;
  }
}
