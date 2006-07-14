package com.intellij.lang.ant.config;

import java.util.EventListener;

public interface AntConfigurationListener extends EventListener {
  void buildFileChanged(AntBuildFile buildFile);
  void buildFileAdded(AntBuildFile buildFile);
  void buildFileRemoved(AntBuildFile buildFile);
}
