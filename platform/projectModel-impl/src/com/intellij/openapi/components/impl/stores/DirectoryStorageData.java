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
package com.intellij.openapi.components.impl.stores;

import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Set;

public class DirectoryStorageData implements StorageDataBase {
  public final Map<String, StateMap> states;

  private DirectoryStorageData(@NotNull Map<String, StateMap> states) {
    this.states = states;
  }

  @NotNull
  public Map<String, Map<String, Object>> toMap() {
    THashMap<String, Map<String, Object>> map = new THashMap<String, Map<String, Object>>(states.size());
    for (Map.Entry<String, StateMap> entry : states.entrySet()) {
      map.put(entry.getKey(), entry.getValue().toMap());
    }
    return map;
  }

  @NotNull
  public static DirectoryStorageData fromMap(@NotNull Map<String, Map<String, ?>> map) {
    Map<String, StateMap> states = new THashMap<String, StateMap>(map.size());
    for (Map.Entry<String, Map<String, ?>> entry : map.entrySet()) {
      states.put(entry.getKey(), StateMap.fromMap(entry.getValue()));
    }
    return new DirectoryStorageData(states);
  }

  @NotNull
  public Set<String> getComponentNames() {
    return states.keySet();
  }

  boolean isEmpty() {
    return states.isEmpty();
  }

  public void clear() {
    states.clear();
  }

  @Override
  public boolean hasState(@NotNull String componentName) {
    StateMap fileToState = states.get(componentName);
    return fileToState != null && fileToState.hasStates();
  }
}
