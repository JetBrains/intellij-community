// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.conflicts;

import com.intellij.openapi.components.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.changes.ChangeListManagerImpl;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@State(name = "ChangelistConflictSettings", storages = @Storage(StoragePathMacros.WORKSPACE_FILE))
public class ChangelistConflictSettings implements PersistentStateComponent<ChangelistConflictSettings> {
  @Nullable private final Project myProject;

  public boolean TRACKING_ENABLED = true;
  public boolean SHOW_DIALOG = false;
  public boolean HIGHLIGHT_CONFLICTS = true;
  public boolean HIGHLIGHT_NON_ACTIVE_CHANGELIST = false;
  public ChangelistConflictResolution LAST_RESOLUTION = ChangelistConflictResolution.IGNORE;

  public ChangelistConflictSettings() {
    myProject = null;
  }

  public ChangelistConflictSettings(@NotNull Project project) {
    myProject = project;
  }

  public static ChangelistConflictSettings getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, ChangelistConflictSettings.class);
  }

  @Nullable
  @Override
  public ChangelistConflictSettings getState() {
    return this;
  }

  @Override
  public void loadState(@NotNull ChangelistConflictSettings state) {
    XmlSerializerUtil.copyBean(state, this);
  }

  @Override
  public void noStateLoaded() {
    if (myProject == null || myProject.isDefault()) return;
    ChangelistConflictTracker.Options oldOptions = ChangeListManagerImpl.getInstanceImpl(myProject).getConflictTracker().getOldOptions();
    TRACKING_ENABLED = oldOptions.TRACKING_ENABLED;
    SHOW_DIALOG = oldOptions.SHOW_DIALOG;
    HIGHLIGHT_CONFLICTS = oldOptions.HIGHLIGHT_CONFLICTS;
    HIGHLIGHT_NON_ACTIVE_CHANGELIST = oldOptions.HIGHLIGHT_NON_ACTIVE_CHANGELIST;
    LAST_RESOLUTION = oldOptions.LAST_RESOLUTION;
  }
}
