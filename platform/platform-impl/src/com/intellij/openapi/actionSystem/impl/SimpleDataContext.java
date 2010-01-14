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
package com.intellij.openapi.actionSystem.impl;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.Project;

public class SimpleDataContext implements DataContext {
  private final String myDataId;
  private final Object myData;
  private final DataContext myParent;

  private SimpleDataContext(String dataId, Object data, DataContext parent) {
    myDataId = dataId;
    myData = data;
    myParent = parent;
  }

  public Object getData(String dataId) {
    return myDataId.equals(dataId) ? myData : myParent == null ? null : myParent.getData(dataId);
  }

  public static DataContext getSimpleContext(String dataId, Object data, DataContext parent) {
    return new SimpleDataContext(dataId, data, parent);
  }

  public static DataContext getSimpleContext(String dataId, Object data) {
    return getSimpleContext(dataId, data, null);
  }

  public static DataContext getProjectContext(Project project) {
    return getSimpleContext(PlatformDataKeys.PROJECT.getName(), project);
  }
}
