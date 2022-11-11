// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.common;

import com.intellij.util.ReflectionUtil;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.TestOnly;

import java.util.Hashtable;
import java.util.Map;

@TestOnly
@Internal
public final class Cleanup {

  private Cleanup() { }

  @SuppressWarnings("ConstantConditions")
  public static void cleanupSwingDataStructures() throws Exception {
    Object manager = ReflectionUtil.getDeclaredMethod(Class.forName("javax.swing.KeyboardManager"), "getCurrentManager").invoke(null);
    Map<?, ?> componentKeyStrokeMap = ReflectionUtil.getField(manager.getClass(), manager, Hashtable.class, "componentKeyStrokeMap");
    componentKeyStrokeMap.clear();
    Map<?, ?> containerMap = ReflectionUtil.getField(manager.getClass(), manager, Hashtable.class, "containerMap");
    containerMap.clear();
  }
}
