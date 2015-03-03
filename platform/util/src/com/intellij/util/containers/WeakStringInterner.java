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
package com.intellij.util.containers;

import com.intellij.util.ConcurrencyUtil;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;

import java.util.Set;
import java.util.concurrent.ConcurrentMap;

/**
 * @author peter
 */
public class WeakStringInterner extends StringInterner {
  private final ConcurrentMap<String, String> myMap = ContainerUtil.createConcurrentWeakKeyWeakValueMap();
  
  @NotNull
  @Override
  public String intern(@NotNull String name) {
    return ConcurrencyUtil.cacheOrGet(myMap, name, name);
  }

  @Override
  public void clear() {
    myMap.clear();
  }

  @NotNull
  @Override
  public Set<String> getValues() {
    return new THashSet<String>(myMap.values());
  }
}
