// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.usages;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.Nullable;

@State(
  name = "ShowUsagesSettings",
  storages = {
    @Storage("usageView.xml")
  }
)
public class ShowUsagesSettings implements PersistentStateComponent<ShowUsagesSettings.MyUsageViewSettings> {
  private final MyUsageViewSettings myState = new MyUsageViewSettings();

  @Nullable
  @Override
  public MyUsageViewSettings getState() {
    return myState;
  }

  @Override
  public void loadState(MyUsageViewSettings state) {
    XmlSerializerUtil.copyBean(state, myState);
  }

  public void loadState(UsageViewSettings state) {
    XmlSerializerUtil.copyBean(state, myState);
  }

  public static ShowUsagesSettings getInstance() {
    return ServiceManager.getService(ShowUsagesSettings.class);
  }

  static class MyUsageViewSettings extends UsageViewSettings {
    public MyUsageViewSettings() {
      GROUP_BY_FILE_STRUCTURE = false;
      GROUP_BY_MODULE = false;
      GROUP_BY_PACKAGE = false;
      GROUP_BY_USAGE_TYPE = false;
      GROUP_BY_SCOPE = false;
    }
  }
}
