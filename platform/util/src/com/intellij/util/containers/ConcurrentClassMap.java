// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.containers;

import java.util.concurrent.ConcurrentHashMap;

/**
 * @author peter
 */
public class ConcurrentClassMap<T> extends ClassMap<T> {
  public ConcurrentClassMap() {
    super(new ConcurrentHashMap<Class<?>, T>());
  }
}
