package com.intellij.structuralsearch.plugin.ui;

/**
 * Created by IntelliJ IDEA.
 * User: Maxim.Mossienko
 * Date: Mar 25, 2004
 * Time: 1:33:10 PM
 * To change this template use File | Settings | File Templates.
 */
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
