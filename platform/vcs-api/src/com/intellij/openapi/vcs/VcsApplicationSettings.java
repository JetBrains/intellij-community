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
package com.intellij.openapi.vcs;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.xmlb.XmlSerializerUtil;

/**
 * We don't use roaming type PER_OS - path macros is enough ($USER_HOME$/Dropbox for example)
 */
@State(
  name = "VcsApplicationSettings",
  storages = @Storage("vcs.xml")
)
public class VcsApplicationSettings implements PersistentStateComponent<VcsApplicationSettings> {
  public String PATCH_STORAGE_LOCATION = null;
  public boolean SHOW_WHITESPACES_IN_LST = false;
  public boolean SHOW_LST_GUTTER_MARKERS = true;
  public boolean SHOW_LST_WORD_DIFFERENCES = true;

  public static VcsApplicationSettings getInstance() {
    return ServiceManager.getService(VcsApplicationSettings.class);
  }

  @Override
  public VcsApplicationSettings getState() {
    return this;
  }

  @Override
  public void loadState(VcsApplicationSettings state) {
    XmlSerializerUtil.copyBean(state, this);
  }
}
