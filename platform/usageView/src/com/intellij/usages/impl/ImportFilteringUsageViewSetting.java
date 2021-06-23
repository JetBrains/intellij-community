// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.usages.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;

@Service
@State(name = "ImportFilteringUsageViewSetting", storages = @Storage("usageView.xml"))
public final class ImportFilteringUsageViewSetting implements PersistentStateComponent<ImportFilteringUsageViewSetting> {

  public static ImportFilteringUsageViewSetting getInstance() {
    return ApplicationManager.getApplication().getService(ImportFilteringUsageViewSetting.class);
  }

  public boolean SHOW_IMPORTS = true;

  @Override
  public ImportFilteringUsageViewSetting getState() {
    return this;
  }

  @Override
  public void loadState(@NotNull final ImportFilteringUsageViewSetting state) {
    XmlSerializerUtil.copyBean(state, this);
  }
}
