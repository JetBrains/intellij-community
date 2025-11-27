// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.*;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * We don't use roaming type PER_OS - path macros is enough ($USER_HOME$/Dropbox for example)
 */
@State(
  name = VcsApplicationSettings.SETTINGS_KEY,
  storages = @Storage("vcs.xml"),
  category = SettingsCategory.TOOLS
)
@Service
public final class VcsApplicationSettings implements PersistentStateComponent<VcsApplicationSettings> {
  @ApiStatus.Internal
  public static final String SETTINGS_KEY = "VcsApplicationSettings";

  public String PATCH_STORAGE_LOCATION = null;
  public boolean SHOW_WHITESPACES_IN_LST = true;
  public boolean SHOW_LST_GUTTER_MARKERS = true;
  public boolean SHOW_LST_ERROR_STRIPE_MARKERS = true;
  public boolean DETECT_PATCH_ON_THE_FLY = false;
  public boolean CREATE_CHANGELISTS_AUTOMATICALLY = false;
  public boolean ENABLE_PARTIAL_CHANGELISTS = true;
  public boolean MANAGE_IGNORE_FILES = false;
  public boolean DISABLE_MANAGE_IGNORE_FILES = false;
  public boolean MARK_EXCLUDED_AS_IGNORED = true;
  public boolean COMMIT_FROM_LOCAL_CHANGES = true;
  /**
   * Option to show editor diff preview in non-modal commit interface with Commit toolwindow.
   */
  public boolean SHOW_EDITOR_PREVIEW_ON_DOUBLE_CLICK = true;
  /**
   * Option to show editor diff preview in modal commit interface with Local Changes toolwindow tab.
   */
  public boolean SHOW_DIFF_ON_DOUBLE_CLICK = false;

  public static VcsApplicationSettings getInstance() {
    return ApplicationManager.getApplication().getService(VcsApplicationSettings.class);
  }

  @Override
  public VcsApplicationSettings getState() {
    return this;
  }

  @Override
  public void loadState(@NotNull VcsApplicationSettings state) {
    XmlSerializerUtil.copyBean(state, this);
  }

  @Override
  public void noStateLoaded() {
    loadState(new VcsApplicationSettings());
  }
}
