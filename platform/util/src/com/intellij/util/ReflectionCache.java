/*
 * Copyright 2000-2019 JetBrains s.r.o.
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

import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 * @deprecated Contrary to the name, this class doesn't do any caching. So the usages may be safely dropped in favor of plain reflection calls.
 * <p>
 * Consider caching higher-level things, if you see reflection in your snapshots.
 */
@Deprecated
public class ReflectionCache {

  /**
   * @deprecated doesn't cache
   */
  @Deprecated
  public static boolean isAssignable(@NotNull Class ancestor, Class descendant) {
    return ancestor == descendant || ancestor.isAssignableFrom(descendant);
  }

  /**
   * @deprecated doesn't cache
   */
  @Deprecated
  public static boolean isInstance(Object instance, @NotNull Class clazz) {
    return clazz.isInstance(instance);
  }
}
