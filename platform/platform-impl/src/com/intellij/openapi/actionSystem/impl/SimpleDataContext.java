// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.actionSystem.impl;

import com.intellij.ide.DataManager;
import com.intellij.ide.impl.DataManagerImpl;
import com.intellij.ide.impl.dataRules.GetDataRule;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.IdeFocusManager;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Map;

public final class SimpleDataContext implements DataContext {
  private final Map<String, Object> myDataId2Data;
  private final DataContext myParent;

  private SimpleDataContext(@NotNull Map<String, Object> dataId2data, DataContext parent) {
    myDataId2Data = dataId2data;
    myParent = parent;
  }

  @Override
  public Object getData(@NotNull String dataId) {
    Object result = getDataFromSelfOrParent(dataId);

    if (result == null && PlatformDataKeys.CONTEXT_COMPONENT.getName().equals(dataId)) {
      result = IdeFocusManager.getGlobalInstance().getFocusOwner();
    }

    if (result == null) {
      GetDataRule rule = ((DataManagerImpl)DataManager.getInstance()).getDataRule(dataId);
      if (rule != null) {
        return rule.getData(this::getDataFromSelfOrParent);
      }
    }

    return result;
  }

  private Object getDataFromSelfOrParent(@NotNull String dataId) {
    return myDataId2Data.containsKey(dataId) ? myDataId2Data.get(dataId) :
           myParent == null ? null : myParent.getData(dataId);
  }

  @NotNull
  public static DataContext getSimpleContext(@NotNull String dataId, Object data, DataContext parent) {
    return new SimpleDataContext(data == null ? Collections.emptyMap() : Map.of(dataId, data), parent);
  }

  @NotNull
  public static DataContext getSimpleContext(@NotNull Map<String,Object> dataId2data, DataContext parent) {
    return new SimpleDataContext(dataId2data, parent);
  }

  @NotNull
  public static DataContext getSimpleContext(@NotNull String dataId, Object data) {
    return getSimpleContext(dataId, data, null);
  }

  @NotNull
  public static DataContext getProjectContext(Project project) {
    return getSimpleContext(CommonDataKeys.PROJECT.getName(), project);
  }
}
