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

import com.intellij.reference.SoftReference;
import com.intellij.util.ConcurrencyUtil;
import org.jetbrains.annotations.NotNull;

import java.lang.ref.WeakReference;
import java.util.HashSet;
import java.util.Set;

/**
 * @author peter
 */
public class WeakStringInterner extends StringInterner {
  private final ConcurrentWeakHashMap<String, WeakReference<String>> myMap = new ConcurrentWeakHashMap<String, WeakReference<String>>();
  
  @NotNull
  @Override
  public String intern(@NotNull String name) {
    WeakReference<String> key = new WeakReference<String>(name);
    String interned = SoftReference.dereference(ConcurrencyUtil.cacheOrGet(myMap, name, key));
    if (interned != null) return interned;

    myMap.put(name, key);
    return name;
  }

  @Override
  public void clear() {
    myMap.clear();
  }

  @NotNull
  @Override
  public Set<String> getValues() {
    HashSet<String> result = ContainerUtil.newHashSet();
    for (WeakReference<String> value : myMap.values()) {
      ContainerUtil.addIfNotNull(result, value.get());
    }
    return result;
  }
}
