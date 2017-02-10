/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.openapi.diff.impl;

import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.actionSystem.DataProvider;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

public class GenericDataProvider implements DataProvider {
  private final Map<String, Object> myGenericData;
  private final DataProvider myParentProvider;

  public GenericDataProvider() {
    this(null);
  }

  public GenericDataProvider(@Nullable DataProvider provider) {
    myParentProvider = provider;
    myGenericData = new HashMap<>();
  }

  public void putData(DataKey key, Object value) {
    myGenericData.put(key.getName(), value);
  }

  public void putData(String key, Object value) {
    myGenericData.put(key, value);
  }

  public void putData(final Map<String, Object> map) {
    myGenericData.putAll(map);
  }

  public Object getData(String dataId) {
    Object data = myGenericData.get(dataId);
    if (data != null) return data;
    return myParentProvider != null ? myParentProvider.getData(dataId) : null;
  }
}
