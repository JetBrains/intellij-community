package org.jetbrains.jps.appengine.model;

import org.jetbrains.jps.model.JpsElement;
import org.jetbrains.jps.model.module.JpsModule;

import java.util.List;

/**
 * @author nik
 */
public interface JpsAppEngineModuleExtension extends JpsElement {
  JpsModule getModule();

  String getSdkHomePath();

  boolean isRunEnhancerOnMake();

  List<String> getFilesToEnhance();

  PersistenceApi getPersistenceApi();

  String getToolsApiJarPath();
}
