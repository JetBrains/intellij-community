// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.common;

import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.TestOnly;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Hashtable;
import java.util.Map;

@TestOnly
@Internal
public final class Cleanup {
  private Cleanup() { }

  private static final MethodHandle currentManagerHandle;
  private static final MethodHandle componentKeyStrokeMapHandle;
  private static final MethodHandle containerMapHandle;

  static {
    try {
      Class<?> aClass = Cleanup.class.getClassLoader().loadClass("javax.swing.KeyboardManager");
      MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(aClass, MethodHandles.lookup());
      currentManagerHandle = lookup.findStatic(aClass, "getCurrentManager", MethodType.methodType(aClass));

      componentKeyStrokeMapHandle = lookup.findGetter(aClass, "componentKeyStrokeMap", Hashtable.class);
      containerMapHandle = lookup.findGetter(aClass, "containerMap", Hashtable.class);
    }
    catch (Throwable e) {
      throw new RuntimeException(e);
    }
  }

  public static void cleanupSwingDataStructures() throws Throwable {
    Object manager = currentManagerHandle.invoke();
    Map<?, ?> componentKeyStrokeMap = (Map<?, ?>)componentKeyStrokeMapHandle.invoke(manager);
    componentKeyStrokeMap.clear();
    Map<?, ?> containerMap = (Map<?, ?>)containerMapHandle.invoke(manager);
    containerMap.clear();
  }
}
