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
package com.intellij.testFramework;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DataKey;

import java.util.HashMap;
import java.util.Map;

public class MapDataContext implements DataContext {
  private final Map myMap = new HashMap();

  @Override
  public Object getData(String dataId) {
    return myMap.get(dataId);
  }

  public void put(String dataId, Object data) {
    myMap.put(dataId, data);
  }

  public <T> void put(DataKey<T> dataKey, T data) {
    put(dataKey.getName(), data);
  }
}
