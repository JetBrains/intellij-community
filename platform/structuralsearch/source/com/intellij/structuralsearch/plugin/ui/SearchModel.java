package com.intellij.structuralsearch.plugin.ui;

public class SearchModel {
  private final Configuration config;
  private Configuration shadowConfig;

  public SearchModel(Configuration config) {
    this.config = config;
  }

  public Configuration getConfig() {
    return config;
  }

  public void setShadowConfig(Configuration shadowConfig) {
    this.shadowConfig = shadowConfig;
  }

  public Configuration getShadowConfig() {
    return shadowConfig;
  }
}
