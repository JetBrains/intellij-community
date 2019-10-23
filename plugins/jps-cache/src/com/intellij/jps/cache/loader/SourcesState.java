package com.intellij.jps.cache.loader;

import java.util.Map;

public class SourcesState {
  private final Map<String, String> production;
  private final Map<String, String> test;

  public SourcesState(Map<String, String> production, Map<String, String> test) {
    this.production = production;
    this.test = test;
  }

  public Map<String, String> getProduction() {
    return production;
  }

  public Map<String, String> getTest() {
    return test;
  }
}