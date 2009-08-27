package com.intellij.appengine.facet;

/**
* @author nik
*/
public enum PersistenceApi {
  JDO("JDO"), JPA("JPA");
  private final String myName;

  PersistenceApi(String name) {
    myName = name;
  }

  public String getName() {
    return myName;
  }
}
