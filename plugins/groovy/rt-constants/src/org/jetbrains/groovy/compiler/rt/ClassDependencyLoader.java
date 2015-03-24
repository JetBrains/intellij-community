/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package org.jetbrains.groovy.compiler.rt;

import java.lang.reflect.*;
import java.util.HashSet;
import java.util.Set;

/**
 * @author peter
 */
public class ClassDependencyLoader {

  /**
   * @param aClass
   * @return aClass
   * @throws ClassNotFoundException when any of the classes can't be loaded, that's referenced in aClass' fields, methods etc. recursively 
   */
  public Class loadDependencies(Class aClass) throws ClassNotFoundException {
    loadClassDependencies(aClass, new HashSet<Class>());
    return aClass;
  }

  private void loadTypeDependencies(Type aClass, Set<Class> visited) throws ClassNotFoundException {
    if (aClass instanceof Class) {
      loadClassDependencies((Class)aClass, visited);
    }
    else if (aClass instanceof ParameterizedType) {
      loadTypeDependencies(((ParameterizedType)aClass).getOwnerType(), visited);
      for (Type type : ((ParameterizedType)aClass).getActualTypeArguments()) {
        loadTypeDependencies(type, visited);
      }
    }
    else if (aClass instanceof WildcardType) {
      for (Type type : ((WildcardType)aClass).getLowerBounds()) {
        loadTypeDependencies(type, visited);
      }
      for (Type type : ((WildcardType)aClass).getUpperBounds()) {
        loadTypeDependencies(type, visited);
      }
    }
    else if (aClass instanceof GenericArrayType) {
      loadTypeDependencies(((GenericArrayType)aClass).getGenericComponentType(), visited);
    }
  }

  protected void loadClassDependencies(Class aClass, Set<Class> visited) throws ClassNotFoundException {
    String name = aClass.getName();
    if (visited.add(aClass)) {
      try {
        for (Method method : aClass.getDeclaredMethods()) {
          loadTypeDependencies(method.getGenericReturnType(), visited);
          for (Type type : method.getGenericExceptionTypes()) {
            loadTypeDependencies(type, visited);
          }
          for (Type type : method.getGenericParameterTypes()) {
            loadTypeDependencies(type, visited);
          }
        }
        for (Constructor method : aClass.getDeclaredConstructors()) {
          for (Type type : method.getGenericExceptionTypes()) {
            loadTypeDependencies(type, visited);
          }
          for (Type type : method.getGenericParameterTypes()) {
            loadTypeDependencies(type, visited);
          }
        }

        for (Field field : aClass.getDeclaredFields()) {
          loadTypeDependencies(field.getGenericType(), visited);
        }

        Type superclass = aClass.getGenericSuperclass();
        if (superclass != null) {
          loadClassDependencies(aClass, visited);
        }

        for (Type intf : aClass.getGenericInterfaces()) {
          loadTypeDependencies(intf, visited);
        }

        aClass.getAnnotations();
        Package aPackage = aClass.getPackage();
        if (aPackage != null) {
          aPackage.getAnnotations();
        }
      }
      catch (LinkageError e) {
        throw new ClassNotFoundException(name);
      }
      catch (TypeNotPresentException e) {
        throw new ClassNotFoundException(name);
      }
    }
  }

}
