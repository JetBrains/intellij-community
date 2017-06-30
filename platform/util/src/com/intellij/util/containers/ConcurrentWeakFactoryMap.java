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

package com.intellij.util.containers;

import com.intellij.util.Function;
import com.intellij.util.Producer;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.ConcurrentMap;

/**
 * @author peter
 */
public abstract class ConcurrentWeakFactoryMap extends ConcurrentFactoryMap {
  /**
   * Use {@link #createWeakMap(Function)} instead
   * TODO to remove in IDEA 2018
   */
  @Deprecated
  public ConcurrentWeakFactoryMap() {
  }

  @NotNull
  public static <T, V> ConcurrentMap<T, V> createWeakMap(@NotNull Function<T, V> compute) {
    return ConcurrentFactoryMap.createMap(compute, new Producer<ConcurrentMap<T, V>>() {
      @Override
      public ConcurrentMap<T, V> produce() {return ContainerUtil.createConcurrentWeakMap();}
    });
  }
}