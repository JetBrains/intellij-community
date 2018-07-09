// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.actionSystem.impl;

import com.intellij.ide.DataManager;
import com.intellij.ide.impl.DataManagerImpl;
import com.intellij.ide.impl.dataRules.GetDataRule;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.IdeFocusManager;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

public class SimpleDataContext implements DataContext {
  private final Map<String, Object> myDataId2Data;
  private final DataContext myParent;
  private boolean myWithRules = false;
  private DataProvider myDataProvider;

  private SimpleDataContext(String dataId, Object data, DataContext parent) {
    myDataId2Data = new HashMap<>(1);
    myDataId2Data.put(dataId, data);
    myParent = parent;
  }
  
  private SimpleDataContext(Map<String, Object> dataid2data, DataContext parent, boolean withRules) {
    myDataId2Data = dataid2data;
    myParent = parent;
    myWithRules = withRules;
    if (withRules) {
      myDataProvider = new DataProvider() {
        @Nullable
        @Override
        public Object getData(String dataId) {
          return myDataId2Data.get(dataId);
        }
      };
    }
  }

  @Override
  public Object getData(String dataId) {
    Object result =  myDataId2Data.containsKey(dataId) ? myDataId2Data.get(dataId) : 
           myParent == null ? null : myParent.getData(dataId);
    
    if (result == null && PlatformDataKeys.CONTEXT_COMPONENT.getName().equals(dataId)) {
      result = IdeFocusManager.getGlobalInstance().getFocusOwner();
    }

    if (result == null && myWithRules) {
      GetDataRule rule = ((DataManagerImpl)DataManager.getInstance()).getDataRule(dataId);
      if (rule != null) {
        return rule.getData(myDataProvider);
      }
    }

    return result;
  }

  public static DataContext getSimpleContext(String dataId, Object data, DataContext parent) {
    return new SimpleDataContext(dataId, data, parent);
  }
  
  public static DataContext getSimpleContext(Map<String,Object> dataId2data, DataContext parent) {
    return new SimpleDataContext(dataId2data, parent, false);
  }

  /**
   * Creates a simple data context which can apply data rules.
   */
  public static DataContext getSimpleContext(Map<String, Object> dataId2data, DataContext parent, boolean withRules) {
    return new SimpleDataContext(dataId2data, parent, withRules);
  }

  public static DataContext getSimpleContext(String dataId, Object data) {
    return getSimpleContext(dataId, data, null);
  }

  public static DataContext getProjectContext(Project project) {
    return getSimpleContext(CommonDataKeys.PROJECT.getName(), project);
  }
}
