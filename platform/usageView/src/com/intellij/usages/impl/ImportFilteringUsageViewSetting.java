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
package com.intellij.usages.impl;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.xmlb.XmlSerializerUtil;

/**
 * @author yole
 */
@State(
  name = "ImportFilteringUsageViewSetting",
  storages = {
    @Storage("usageView.xml"),
    @Storage(value = "other.xml", deprecated = true)
  }
)
public class ImportFilteringUsageViewSetting implements PersistentStateComponent<ImportFilteringUsageViewSetting> {
  public static ImportFilteringUsageViewSetting getInstance() {
    return ServiceManager.getService(ImportFilteringUsageViewSetting.class);
  }

  public boolean SHOW_IMPORTS = true;

  @Override
  public ImportFilteringUsageViewSetting getState() {
    return this;
  }

  @Override
  public void loadState(final ImportFilteringUsageViewSetting state) {
    XmlSerializerUtil.copyBean(state, this);
  }
}
