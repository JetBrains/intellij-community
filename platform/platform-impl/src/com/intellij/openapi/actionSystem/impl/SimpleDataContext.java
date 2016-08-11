/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.util.containers.HashMap;

import java.util.Map;

public class SimpleDataContext implements DataContext {
  private final Map<String, Object> myDataId2Data;
  private final DataContext myParent;

  private SimpleDataContext(String dataId, Object data, DataContext parent) {
    myDataId2Data = new HashMap<>(1);
    myDataId2Data.put(dataId, data);
    myParent = parent;
  }
  
  private SimpleDataContext(Map<String, Object> dataid2data, DataContext parent) {
    myDataId2Data = dataid2data;
    myParent = parent;
  }

  @Override
  public Object getData(String dataId) {
    Object result =  myDataId2Data.containsKey(dataId) ? myDataId2Data.get(dataId) : 
           myParent == null ? null : myParent.getData(dataId);
    
    if (result == null && PlatformDataKeys.CONTEXT_COMPONENT.getName().equals(dataId)) {
      result = IdeFocusManager.getGlobalInstance().getFocusOwner();
    }

    return result;
  }

  public static DataContext getSimpleContext(String dataId, Object data, DataContext parent) {
    return new SimpleDataContext(dataId, data, parent);
  }
  
  public static DataContext getSimpleContext(Map<String,Object> dataId2data, DataContext parent) {
    return new SimpleDataContext(dataId2data, parent);
  }

  public static DataContext getSimpleContext(String dataId, Object data) {
    return getSimpleContext(dataId, data, null);
  }

  public static DataContext getProjectContext(Project project) {
    return getSimpleContext(CommonDataKeys.PROJECT.getName(), project);
  }
}
