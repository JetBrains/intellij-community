// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.status.widget;

import com.intellij.openapi.components.*;
import com.intellij.openapi.wm.StatusBarWidgetFactory;
import com.intellij.util.xmlb.XmlSerializerUtil;
import com.intellij.util.xmlb.annotations.Tag;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

@Service
@State(name = "StatusBar", storages = @Storage(value = StoragePathMacros.WORKSPACE_FILE, roamingType = RoamingType.DISABLED))
public final class StatusBarWidgetSettings extends BaseState implements PersistentStateComponent<StatusBarWidgetSettings> {
  @Tag("disabled-widgets")
  private final Set<String> disabledWidgets = new HashSet<>();

  @NotNull
  @Override
  public StatusBarWidgetSettings getState() {
    return this;
  }

  @Override
  public void loadState(@NotNull StatusBarWidgetSettings state) {
    XmlSerializerUtil.copyBean(state, this);
  }

  public boolean isEnabled(@NotNull StatusBarWidgetFactory factory) {
    return !disabledWidgets.contains(factory.getId());
  }

  public void setEnabled(@NotNull StatusBarWidgetFactory factory, boolean state) {
    if (state) {
      if (disabledWidgets.remove(factory.getId())) {
        incrementModificationCount();
      }
    }
    else {
      if (disabledWidgets.add(factory.getId())) {
        incrementModificationCount();
      }
    }
  }
}
