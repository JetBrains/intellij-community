// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.collectors.fus.ui.persistence;

import com.intellij.internal.statistic.persistence.UsageStatisticsPersistenceComponent;
import com.intellij.openapi.components.*;
import com.intellij.util.xmlb.annotations.MapAnnotation;
import com.intellij.util.xmlb.annotations.Tag;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Konstantin Bulenkov
 */
@State(
  name = "ShortcutsCollector",
  storages = {
    @Storage(value = UsageStatisticsPersistenceComponent.USAGE_STATISTICS_XML, roamingType = RoamingType.DISABLED, deprecated = true),
    @Storage(value = "statistics.shortcuts.xml", roamingType = RoamingType.DISABLED, deprecated = true)
  }
)
public class ShortcutsCollector implements PersistentStateComponent<ShortcutsCollector.MyState> {
  public final static class MyState {
    @Tag("counts")
    @MapAnnotation(surroundWithTag = false, keyAttributeName = "shortcut", valueAttributeName = "count")
    public final Map<String, Integer> myValues = new HashMap<>();
  }

  private final MyState myState = new MyState();

  @Override
  @NotNull
  public MyState getState() {
    return myState;
  }

  @Override
  public void loadState(@NotNull final MyState state) {
  }

  public static ShortcutsCollector getInstance() {
    return ServiceManager.getService(ShortcutsCollector.class);
  }
}
