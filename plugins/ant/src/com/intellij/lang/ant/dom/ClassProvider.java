/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.lang.ant.dom;

import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
* @author Eugene Zhuravlev
*/
abstract class ClassProvider {
  static final ClassProvider EMPTY = new ClassProvider() {
    @Nullable
    @Override
    public Class lookupClass() {
      return null;
    }

    @Nullable
    @Override
    public String getError() {
      return null;
    }
  };
  @Nullable
  abstract Class lookupClass();

  @Nullable
  abstract String getError();

  static ClassProvider create(Class clazz) {
    return clazz == null? EMPTY : new LoadedClassProvider(clazz);
  }

  static ClassProvider create(@Nullable String className, @NotNull ClassLoader loader) {
    return className == null? EMPTY : new LazyLoadClassProvider(className, loader);
  }

  private static final class LoadedClassProvider extends ClassProvider {
    private final Class myClass;

    LoadedClassProvider(Class clazz) {
      myClass = clazz;
    }

    @Nullable
    @Override
    public Class lookupClass() {
      return myClass;
    }

    @Nullable
    @Override
    public String getError() {
      return null;
    }
  }

  private static final class LazyLoadClassProvider extends ClassProvider {
    private final String myClassName;
    private final ClassLoader myClassLoader;
    private Pair<Class, String> myResult;

    LazyLoadClassProvider(String className, ClassLoader classLoader) {
      myClassName = className;
      myClassLoader = classLoader;
    }

    @Override
    @Nullable
    public Class lookupClass() {
      return getResult().getFirst();
    }

    @Override
    @Nullable
    public String getError() {
      return getResult().getSecond();
    }

    @NotNull
    private Pair<Class, String> getResult() {
      Pair<Class, String> result = myResult;
      if (result == null) {
        Class clazz = null;
        String error = null;
        try {
          clazz = myClassLoader.loadClass(myClassName);
        }
        catch (ClassNotFoundException e) {
          error = "Class not found " + e.getMessage();
        }
        catch (NoClassDefFoundError e) {
          error = "Class definition not found " + e.getMessage();
        }
        catch (UnsupportedClassVersionError e) {
          error = "Unsupported class version " + e.getMessage();
        }
        myResult = result = Pair.create(clazz, error);
      }
      return result;
    }
  }

}
