package com.intellij.appengine.facet;

/**
* @author nik
*/
public enum PersistenceApi {
  JDO("JDO 2", "JDO", 1), JDO3("JDO 3", "JDO", 2), JPA("JPA 1", "JPA", 1), JPA2("JPA 2", "JPA", 2);
  private final String myDisplayName;
  private final String myEnhancerApiName;
  private final int myEnhancerVersion;

  PersistenceApi(String displayName, String enhancerApiName, int enhancerVersion) {
    myDisplayName = displayName;
    myEnhancerApiName = enhancerApiName;
    myEnhancerVersion = enhancerVersion;
  }

  public String getDisplayName() {
    return myDisplayName;
  }

  public String getEnhancerApiName() {
    return myEnhancerApiName;
  }

  public int getEnhancerVersion() {
    return myEnhancerVersion;
  }
}
