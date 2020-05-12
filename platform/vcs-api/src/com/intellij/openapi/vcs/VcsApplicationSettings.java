// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;

/**
 * We don't use roaming type PER_OS - path macros is enough ($USER_HOME$/Dropbox for example)
 */
@State(
  name = "VcsApplicationSettings",
  storages = @Storage("vcs.xml"),
  reportStatistic = true
)
public class VcsApplicationSettings implements PersistentStateComponent<VcsApplicationSettings> {
  public String PATCH_STORAGE_LOCATION = null;
  public boolean SHOW_WHITESPACES_IN_LST = true;
  public boolean SHOW_LST_GUTTER_MARKERS = true;
  public boolean SHOW_LST_ERROR_STRIPE_MARKERS = true;
  public boolean DETECT_PATCH_ON_THE_FLY = false;
  public boolean ENABLE_PARTIAL_CHANGELISTS = true;
  public boolean MANAGE_IGNORE_FILES = false;
  public boolean DISABLE_MANAGE_IGNORE_FILES = false;
  public boolean MARK_EXCLUDED_AS_IGNORED = true;
  public boolean COMMIT_FROM_LOCAL_CHANGES = false;

  public static VcsApplicationSettings getInstance() {
    return ServiceManager.getService(VcsApplicationSettings.class);
  }

  @Override
  public VcsApplicationSettings getState() {
    return this;
  }

  @Override
  public void loadState(@NotNull VcsApplicationSettings state) {
    XmlSerializerUtil.copyBean(state, this);
  }
}
