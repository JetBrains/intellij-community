/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.testFramework;

import com.intellij.openapi.actionSystem.CustomizedDataContext;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DataKey;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/** @deprecated Use {@link com.intellij.openapi.actionSystem.impl.SimpleDataContext} instead */
@Deprecated(forRemoval = true)
public final class MapDataContext extends CustomizedDataContext {
  private final Map<String, Object> myMap = new HashMap<>();

  public MapDataContext() { }

  public MapDataContext(@NotNull Map<DataKey<?>, Object> context) {
    context.forEach((k, v) -> myMap.put(k.getName(), v));
  }

  @Override
  public @NotNull DataContext getParent() {
    return EMPTY_CONTEXT;
  }

  @Override
  public @Nullable Object getRawCustomData(@NotNull String dataId) {
    return myMap.containsKey(dataId) ?
           Objects.requireNonNullElse(myMap.get(dataId), EXPLICIT_NULL) : null;
  }

  public void put(@NotNull String dataId, Object data) {
    myMap.put(dataId, data);
  }

  public <T> void put(@NotNull DataKey<T> dataKey, T data) {
    put(dataKey.getName(), data);
  }
}