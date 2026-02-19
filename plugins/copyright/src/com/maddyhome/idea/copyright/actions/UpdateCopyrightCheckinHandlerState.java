// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.


package com.maddyhome.idea.copyright.actions;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.openapi.project.Project;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;

@State(name = "UpdateCopyrightCheckinHandler", storages = @Storage(StoragePathMacros.WORKSPACE_FILE))
public class UpdateCopyrightCheckinHandlerState implements PersistentStateComponent<UpdateCopyrightCheckinHandlerState> {
  public boolean UPDATE_COPYRIGHT = false;

  @Override
  public UpdateCopyrightCheckinHandlerState getState() {
    return this;
  }

  @Override
  public void loadState(@NotNull UpdateCopyrightCheckinHandlerState state) {
    XmlSerializerUtil.copyBean(state, this);
  }

  public static UpdateCopyrightCheckinHandlerState getInstance(Project project) {
    return project.getService(UpdateCopyrightCheckinHandlerState.class);
  }
}