package com.intellij.lang.ant.psi.introspection;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public enum AntAttributeType {
  INTEGER,
  BOOLEAN,
  STRING,
  FILE,
  REFERENCE;
  
  
  private static final Map<Class<?>, AntAttributeType> ourClassesMap = new HashMap<Class<?>, AntAttributeType>();
  static {
    ourClassesMap.put(int.class, INTEGER);
    ourClassesMap.put(boolean.class, BOOLEAN);
    ourClassesMap.put(File.class, FILE);
  }
  
  public static AntAttributeType create(Class antDeclaredClass) {
    final AntAttributeType type = ourClassesMap.get(antDeclaredClass);
    if (type != null) {
      return type;
    }
    return STRING; // default attribute type
  }
}
