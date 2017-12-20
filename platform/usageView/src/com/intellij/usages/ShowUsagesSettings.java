/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.usages;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@State(name = "ShowUsagesSettings", storages = @Storage("usageView.xml"))
public class ShowUsagesSettings implements PersistentStateComponent<UsageViewSettings> {
  private final UsageViewSettings myState = new UsageViewSettings();
  
  @Nullable
  @Override
  public UsageViewSettings getState() {
    return myState;
  }

  @Override
  public void loadState(@NotNull UsageViewSettings state) {
    XmlSerializerUtil.copyBean(state, myState);
  }

  public static ShowUsagesSettings getInstance() {
    return ServiceManager.getService(ShowUsagesSettings.class);
  }

  public ShowUsagesSettings() {
    myState.setGroupByFileStructure(false);
    myState.setGroupByModule(false);
    myState.setGroupByPackage(false);
    myState.setGroupByUsageType(false);
    myState.setGroupByScope(false);
  }
}
