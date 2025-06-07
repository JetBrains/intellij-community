// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.ant.dom;

import com.intellij.lang.ant.AntBundle;
import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
* @author Eugene Zhuravlev
*/
abstract class ClassProvider {
  static final ClassProvider EMPTY = new ClassProvider() {
    @Override
    public @Nullable Class lookupClass() {
      return null;
    }

    @Override
    public @Nullable String getError() {
      return null;
    }
  };
  abstract @Nullable Class lookupClass();

  abstract @Nullable @Nls(capitalization = Nls.Capitalization.Sentence) String getError();

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

    @Override
    public @Nullable Class lookupClass() {
      return myClass;
    }

    @Override
    public @Nullable String getError() {
      return null;
    }
  }

  private static final class LazyLoadClassProvider extends ClassProvider {
    private final String myClassName;
    private final ClassLoader myClassLoader;
    private Pair<Class, @Nls String> myResult;

    LazyLoadClassProvider(String className, ClassLoader classLoader) {
      myClassName = className;
      myClassLoader = classLoader;
    }

    @Override
    public @Nullable Class lookupClass() {
      return getResult().getFirst();
    }

    @Override
    public @Nullable @Nls(capitalization = Nls.Capitalization.Sentence) String getError() {
      return getResult().getSecond();
    }

    private @NotNull Pair<Class, @Nls String> getResult() {
      Pair<Class, String> result = myResult;
      if (result == null) {
        Class clazz = null;
        @Nls(capitalization = Nls.Capitalization.Sentence) String error = null;
        try {
          clazz = myClassLoader.loadClass(myClassName);
        }
        catch (ClassNotFoundException e) {
          error = AntBundle.message("ant.error.class.not.found", e.getMessage());
        }
        catch (NoClassDefFoundError e) {
          error = AntBundle.message("ant.error.class.definition.not.found", e.getMessage());
        }
        catch (UnsupportedClassVersionError e) {
          error = AntBundle.message("ant.error.unsupported.class.version", e.getMessage());
        }
        myResult = result = Pair.create(clazz, error);
      }
      return result;
    }
  }

}
