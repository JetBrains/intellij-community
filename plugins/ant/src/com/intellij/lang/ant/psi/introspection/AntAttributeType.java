package com.intellij.lang.ant.psi.introspection;

import org.apache.tools.ant.types.Reference;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public enum AntAttributeType {
  INTEGER,
  BOOLEAN,
  STRING,
  FILE,
  ID_REFERENCE;
  
  
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
    if (isAssignableFrom(Reference.class, antDeclaredClass)) {
      return ID_REFERENCE;
    }
    return STRING; // default attribute type
  }
  
  private static boolean isAssignableFrom(Class interfaceClass, Class antDeclaredClass) {
    final ClassLoader loader = antDeclaredClass.getClassLoader();
    try {
      final Class ifaceClass = loader != null? loader.loadClass(interfaceClass.getName()) : interfaceClass;
      return ifaceClass.isAssignableFrom(antDeclaredClass);
    }
    catch (ClassNotFoundException ignored) {
    }
    return false;
  }
}
