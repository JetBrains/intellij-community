/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
