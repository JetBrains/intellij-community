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
  private final Set<Class> myVisited = new HashSet<Class>();

  /**
   * @param aClass
   * @return aClass
   * @throws ClassNotFoundException when any of the classes can't be loaded, that's referenced in aClass' fields, methods etc. recursively
   */
  public Class loadDependencies(Class aClass) throws ClassNotFoundException {
    loadClassDependencies(aClass);
    return aClass;
  }

  private void loadTypeDependencies(Type aClass) throws ClassNotFoundException {
    if (aClass instanceof Class) {
      loadClassDependencies((Class)aClass);
    }
    else if (aClass instanceof ParameterizedType) {
      loadTypeDependencies(((ParameterizedType)aClass).getOwnerType());
      for (Type type : ((ParameterizedType)aClass).getActualTypeArguments()) {
        loadTypeDependencies(type);
      }
    }
    else if (aClass instanceof WildcardType) {
      for (Type type : ((WildcardType)aClass).getLowerBounds()) {
        loadTypeDependencies(type);
      }
      for (Type type : ((WildcardType)aClass).getUpperBounds()) {
        loadTypeDependencies(type);
      }
    }
    else if (aClass instanceof GenericArrayType) {
      loadTypeDependencies(((GenericArrayType)aClass).getGenericComponentType());
    }
  }

  protected void loadClassDependencies(Class aClass) throws ClassNotFoundException {
    String name = aClass.getName();
    if (myVisited.add(aClass)) {
      try {
        for (Method method : aClass.getDeclaredMethods()) {
          loadTypeDependencies(method.getGenericReturnType());
          for (Type type : method.getGenericExceptionTypes()) {
            loadTypeDependencies(type);
          }
          for (Type type : method.getGenericParameterTypes()) {
            loadTypeDependencies(type);
          }
        }
        for (Constructor method : aClass.getDeclaredConstructors()) {
          for (Type type : method.getGenericExceptionTypes()) {
            loadTypeDependencies(type);
          }
          for (Type type : method.getGenericParameterTypes()) {
            loadTypeDependencies(type);
          }
        }

        for (Field field : aClass.getDeclaredFields()) {
          loadTypeDependencies(field.getGenericType());
        }

        Type superclass = aClass.getGenericSuperclass();
        if (superclass != null) {
          loadClassDependencies(aClass);
        }

        for (Type intf : aClass.getGenericInterfaces()) {
          loadTypeDependencies(intf);
        }

        aClass.getAnnotations();
        Package aPackage = aClass.getPackage();
        if (aPackage != null) {
          aPackage.getAnnotations();
        }
      }
      catch (Error e) {
        myVisited.remove(aClass);
        //noinspection InstanceofCatchParameter
        if (e instanceof LinkageError) {
          throw new ClassNotFoundException(name);
        }
        throw e;
      }
      catch (RuntimeException e) {
        myVisited.remove(aClass);
        //noinspection InstanceofCatchParameter
        if (e instanceof TypeNotPresentException) {
          throw new ClassNotFoundException(name);
        }
        throw e;
      }
    }
  }

}
