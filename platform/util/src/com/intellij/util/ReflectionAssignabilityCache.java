/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.util;

import com.intellij.util.containers.ConcurrentFactoryMap;

/**
 * @author peter
 */
public class ReflectionAssignabilityCache {
  private final ConcurrentFactoryMap<Class, ConcurrentFactoryMap<Class, Boolean>> myCache = new ConcurrentFactoryMap<Class, ConcurrentFactoryMap<Class, Boolean>>() {
    @Override
    protected ConcurrentFactoryMap<Class, Boolean> create(final Class anc) {
      return new ConcurrentFactoryMap<Class, Boolean>() {
        @Override
        protected Boolean create(Class desc) {
          return anc.isAssignableFrom(desc);
        }
      };
    }
  };

  public boolean isAssignable(Class ancestor, Class descendant) {
    return ancestor == descendant || myCache.get(ancestor).get(descendant).booleanValue();
  }

}
