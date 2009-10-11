/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
